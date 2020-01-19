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
; protoc -I=/usr/include -I=/usr/local/include -I=resources/proto --java_out=src/java resources/proto/*.proto
;
;
;
;
; then compile the *.java into *.class (not correct yet):
;
; javac -I=/usr/local/include -d target/classes src/java/com/example/tutorial/Example.java



(import 'com.example.tutorial.Example$Person)
(import 'java.io.ByteArrayInputStream)




(def alice (protobuf/create Example$Person
             {:id 108
              :name "Alice"
              :email "alice@example.com"}))


(def b (-> alice
         (assoc :name "Alice B. Carol")
         (assoc :likes ["climbing" "running" "jumping"])
         (protobuf/->bytes)))

alice
b

(protobuf/bytes-> alice b)

; some additional fooling around
;

(protobuf/schema Example$Person)


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
                                    :vhost "/main"}))

(def some-queue "some.queue")




;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; publisher



(def publisher (publisher/create {:exchange-name "my-exchange"}))



(def server-system (-> (component/system-map
                         :publisher (component/using
                                      publisher
                                      [:rmq-connection])
                         :rmq-connection connection)
                     component/start-system))

(def pub (partial protocol/publish (:publisher server-system)))



; put some kind of pb content, like "alice" on the queue
;
(pub "some.queue" alice)
  ; this one looks "un-mangled" so we just get back an edn map

(pub "some.queue" b)
  ; this one looks "protobuf'd" so the :parsed data is a binary jumble(?)





;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; consumer



; stores the messages we've seen so far
;
(def message-received (atom []))


(defn pb-handler
  [body parsed envelope components]

  (swap! message-received conj {:body body
                                :parsed parsed})
  :ack)


; hook our handler into the mechanism (including error and retry)
;
(def message-consumer (consumer/create {:message-handler-fn pb-handler
                                        :options {:queue-name "some.queue"
                                                  :exchange-name "my-exchange"
                                                  :timeout-seconds 120
                                                  :backoff-interval-seconds 60
                                                  :consumer-threads 4
                                                  :max-retries 3}}))


; this "system" just runs until shutdown, processing messages from "some.queue"
;
(def message-system (-> (component/system-map
                          :rmq-connection connection
                          :monitoring monitoring/BaseMonitoring
                          :consumer (component/using
                                      message-consumer
                                      [:rmq-connection :monitoring]))
                      component/start-system))




(:parsed (get @message-received 0))


; we've received the "b" messages off the queue, but it's "binary", let's
; see if we can recover the edn
;
(def parsed (:parsed (get @message-received 1)))
(def body (:body (get @message-received 1)))
(protobuf/read alice (ByteArrayInputStream. body))

