(ns protobuf-queue
  (:require [com.stuartsierra.component :as component]
            [bunnicula.component.connection :as connection]
            [bunnicula.component.publisher :as publisher]
            [bunnicula.protocol :as protocol]
            [bunnicula.component.monitoring :as monitoring]
            [bunnicula.component.consumer-with-retry :as consumer]
            [protobuf.core :as protobuf]))


;
; compile the *.proto(s) into *.java:
;
; protoc -I=/usr/local/include -I=resources/proto --java_out=src/java resources/proto/*.proto
;
;
;
;
; then compile the *.java into *.class (not correct yet):
;
; javac -I /usr/local/include -d target/classes src/java/com/example/tutorial/Example.java



;;;;;;;;;;;;;;;;;;;;;;;;
;
; let's play a little with protobuf
;

(import 'com.example.tutorial.Example$Person)

; make an "object" of "type" Example$Person
;
; see the java code in:
;
;      src/java/com/example/tutorial/Example.java
;
;
(def alice (protobuf/create Example$Person
             {:id 108
              :name "Alice"
              :email "alice@example.com"}))

; right now it's just a map (with some special properties we'll see later)
alice


(def b (-> alice
         (assoc :name "Alice B. Carol")
         (assoc :likes ["climbing" "running" "jumping"])
         (protobuf/->bytes)))

(def c (-> alice
           (assoc :name "Alice B. Carol")
           (assoc :likes ["climbing" "running" "jumping"])))

; this is "clojure-y"
alice

; these are "binary"
(protobuf/->bytes alice)
(protobuf/->bytes c)
b

; this is still "clojure-y"
c

; how do we get a CLojure thing back form a protobuf (binary) thing?
;
;    (->bytes)
;
; BUT, need a valid protobuf instance to provide validation stuff to get the
; binary data back, so it MUST be the correct "type"
;
; NOTE: alice does NOT change!
;
(def round-trip (protobuf/bytes-> alice b))

(protobuf/bytes-> (protobuf/create Example$Person {:id 0 :name ""}) b)
round-trip
alice
(= round-trip c)

; some additional fooling around
;
(protobuf/schema Example$Person)


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; Now for some RabbitMQ!
;

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; Make sure
;      rabbitMQ
;           is running!
;
; > rabbitmq-server
;
;
; Make sure you have rabbitMQ configured:
;
;    http://localhost:15672
;
;    vHost      /main
;    exchange   my-exchange
;    queue      some-queue
;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def connection (connection/create {:host "127.0.0.1"
                                    :port 5672
                                    :username "guest"
                                    :password "guest"
                                    :vhost "/bunnicula"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; publisher


; by default Bunnicula serializes every message to JSON prior to publishing it and gets it from JSON after
; consuming it off a queue.
;
; here is the default JSON (using cheshire):
;
;      (defn json-serializer [message]
;        (-> message
;          json/generate-string
;          (.getBytes)))
;
;      (defn json-deserializer [body]
;        (-> body
;          to-string
;          (json/parse-string true)))


; WE DON'T WANT THIS!
;
; Fortunately, Bunnicula also supports supplying your own serialization/deserialization functions:
;
; see https://github.com/nomnom-insights/nomnom.bunnicula/blob/master/doc/components.md#configuration-1
;
; and https://github.com/nomnom-insights/nomnom.bunnicula/blob/master/doc/components.md#configuration-2
;
; in fact, we don't want Bunnicula doing ANYTHING with our protobuf messages.
; we will do the serialization ourselves!
;
; so...
;
(defn no-op-serializer [message]
  (prn "proto-serializer " message)
  message)

(defn no-op-deserializer [body]
  (prn "proto-deserializer " body)
  body)


; use our new serializer to put "b" on the queue
;
(def proto-publisher (publisher/create {:exchange-name "my-exchange"
                                        :serializer no-op-serializer}))

(def proto-server-system (-> (component/system-map
                               :publisher (component/using
                                            proto-publisher
                                            [:rmq-connection])
                               :rmq-connection connection)
                           component/start-system))
(component/stop proto-server-system)

; let's publish!!!
;
(def proto-pub (partial protocol/publish (:publisher proto-server-system)))

; we do our own serialization with (->bytes)
;
(proto-pub "some.queue" (protobuf/->bytes alice))
(proto-pub "some.queue" b)


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; consumer

; this stores the messages we've seen so far
;
(def message-received (atom []))

(defn pb-handler
  [body parsed envelope components]

  (prn "pb-handler " body)

  ; we use (bytes->) to convert the raw data back
  ;
  ; note: we need to pass (bytes-> both he raw data (body) AND a valid protobuf object ('alice')
  ; in order for this to work. this is because protobuf is a TYPED OBJECT system, so we need something
  ; (alice) to do the type enforcement...
  ;
  ; note note: alice is NOT changed (it's immutable, just like other Clojure things)
  ;
  (swap! message-received conj {:body body :converted (protobuf/bytes-> alice body)})
  :ack)


; hook our handler into the mechanism (including error and retry)
;
(def message-consumer (consumer/create {:message-handler-fn pb-handler
                                        :deserializer no-op-deserializer
                                        :options            {:queue-name "some.queue"
                                                             :exchange-name "my-exchange"
                                                             :timeout-seconds 120
                                                             :backoff-interval-seconds 60
                                                             :consumer-threads 4
                                                             :max-retries 3}}))

; this "system" just runs until shutdown[1], processing messages from "some.queue"
;
(def message-system (-> (component/system-map
                          :rmq-connection connection
                          :monitoring monitoring/BaseMonitoring
                          :consumer (component/using
                                      message-consumer
                                      [:rmq-connection :monitoring]))
                      component/start-system))

; [1] or you evaluate this
(component/stop-system message-system)

; did we get back what we put in?
;
@message-received
(= alice (:converted (get @message-received 0)))
(= round-trip
   (:converted (get @message-received 1)))

; YES!!!!!
