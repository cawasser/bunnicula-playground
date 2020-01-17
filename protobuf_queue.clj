(ns protobuf-queue
  (:require [com.stuartsierra.component :as component]
            [bunnicula.component.connection :as connection]
            [bunnicula.component.publisher :as publisher]
            [bunnicula.protocol :as protocol]
            [bunnicula.component.monitoring :as monitoring]
            [bunnicula.component.consumer-with-retry :as consumer]
            [protobuf.core :as pb]))


; protoc -I=resources/proto --java_out=target/java resources/proto/message.proto
;
; javac -d target/classes target/java/com/bunnicula_playground/Message.java



(import '(com.bunnicula_playground Message$Person))




(def alice (protobuf/create Message$Person
             {:id 108
              :name "Alice"
              :email "alice@example.com"}))


(def b (-> alice
         (assoc :name "Alice B. Carol")
         (assoc :likes ["climbing" "running" "jumping"])
         (protobuf/->bytes)))



(protobuf/bytes-> alice b)

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



; this works!
(protocol/publish (:publisher server-system)
  "some.queue"
  {:integration_id 1 :message_id "123"}) ; this works!




;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; consumer



; stores the messages we've seen so far

(def message-received (atom []))

(defn -handler
  [body parsed envelope components]
  (let [{:keys [integration_id message_id]} parsed]

    ;; ... do whatever processing is needed ...
    ;;
    ;; then return one of:
    ;;
    ;;     :ack   - it worked!
    ;;     :error - it didn't work and it wont work in the future (syntax error in the request, etc.)
    ;;     :retry - it didn't work, but retrying it might work the next time (3rd service unavailable, etc).
    ;;
    ;;
    ;; NOTE: this is a "QUEUE" so we don't send the "reply" down this same path, we send the reply some other
    ;;       way, like another queue

    (swap! message-received conj parsed)

    :ack))

