(ns service
  (:require [com.stuartsierra.component :as component]
            [bunnicula.component.connection :as connection]
            [bunnicula.component.publisher :as publisher]
            [bunnicula.protocol :as protocol]
            [bunnicula.component.monitoring :as monitoring]
            [bunnicula.component.consumer-with-retry :as consumer]))




(def connection (connection/create {:host "127.0.0.1"
                                    :port 5672
                                    :username "guest"
                                    :password "guest"
                                    :vhost "/main"}))



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


; this works!
(protocol/publish (:publisher server-system)
  "some.queue"
  {:integration_id 1 :message_id "123"}) ; this works!


; this works!
(def messages [{:integration_id 2 :message_id "99999"}
               {:integration_id 3 :message_id "third"}
               {:integration_id 4 :message_id "4th"}
               {:integration_id 5 :message_id "fifth-symphony"}])

(def pub (partial protocol/publish (:publisher server-system) "some.queue"))

(doall
  (map (partial protocol/publish (:publisher server-system) "some.queue") messages))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; consumer



; stores the messages we've seen so far

(def message-received (atom []))

(defn import-conversation-handler
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

    (swap! message-received conj [integration_id message_id])

    :ack))


(def consumer (consumer/create {:message-handler-fn import-conversation-handler
                                :options {:queue-name "some.queue"
                                          :exchange-name "my-exchange"
                                          :timeout-seconds 120
                                          :backoff-interval-seconds 60
                                          :consumer-threads 4
                                          :max-retries 3}}))

;
; this "system" just runs until shutdown, processing messages from "some.queue"
;
(def client-system (-> (component/system-map
                         :rmq-connection connection
                         :monitoring monitoring/BaseMonitoring
                         :consumer (component/using
                                     consumer
                                     [:rmq-connection :monitoring]))
                     component/start-system))




;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; let's send some more messages

(pub {:integration_id 234 :message_id "we need this"})
message-received ; it shows!


(pub {:integration_id 6783 :message_id "and again"})
message-received ; it shows!
