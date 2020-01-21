(ns service
  (:require [com.stuartsierra.component :as component]
            [bunnicula.component.connection :as connection]
            [bunnicula.component.publisher :as publisher]
            [bunnicula.protocol :as protocol]
            [bunnicula.component.monitoring :as monitoring]
            [bunnicula.component.consumer-with-retry :as consumer]))




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
;    queues     some-queue
;               user-mgmt
;               calculate
;               calculate-result
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
(def user-mgmt-queue "user-mgmt")
(def calculate-queue "calculate")
(def calculate-result-queue "calculate-result")


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
  {:integration_id 1 :message_id "123"})


; this works!
(def messages [{:integration_id 2 :message_id "99999"}
               {:integration_id 3 :message_id "third"}
               {:integration_id 4 :message_id "4th"}
               {:integration_id 5 :message_id "fifth-symphony"}])

(def user-mgmt-messages [{:user "chris" :password "kdfigukds9548dj" :roles [:admin :repo :assign]}])

(def calculate-messages [{:x 100 :y 500}
                         {:x 7 :y 3}])


(doall
  (map (partial pub "some.queue") messages))

(doall
  (map (partial pub user-mgmt-queue) user-mgmt-messages))

(doall
  (map (partial pub calculate-queue) calculate-messages))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; consumer



; stores the messages we've seen so far

(def message-received (atom []))
(def users (atom []))
(def calculations (atom []))

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

(defn user-mgmt-handler
  [body parsed envelope components]
  (let [{:keys [user password roles]} parsed]

    (swap! users conj [user password roles])

    :ack))


(defn calculation-handler
  [body parsed envelope components]
  (let [{:keys [x y]} parsed]

    (pub calculate-result-queue {:x x :y y :result (+ x y)})

    :ack))


(defn calculation-result-handler
  [body parsed envelope components]
  (let [{:keys [x y result]} parsed]

    (swap! calculations conj {:x x :y y :result result})

    :ack))


(def defaults {:options {:exchange-name "my-exchange"
                         :timeout-seconds 120
                         :backoff-interval-seconds 60
                         :consumer-threads 4
                         :max-retries 3}})

(defn setup-queue [handler queue-name]
  (-> defaults
    (assoc :message-handler-fn handler)
    (assoc-in [:options :queue-name] queue-name)))


(def message-consumer (consumer/create {:message-handler-fn import-conversation-handler
                                        :options {:queue-name "some.queue"
                                                  :exchange-name "my-exchange"
                                                  :timeout-seconds 120
                                                  :backoff-interval-seconds 60
                                                  :consumer-threads 4
                                                  :max-retries 3}}))

;
; this "system" just runs until shutdown, processing messages from "some.queue"
;
(def message-system (-> (component/system-map
                          :rmq-connection connection
                          :monitoring monitoring/BaseMonitoring
                          :consumer (component/using
                                      message-consumer
                                      [:rmq-connection :monitoring]))
                      component/start-system))
(component/stop message-system)

(def user-system
  (-> (component/system-map
        :rmq-connection connection
        :monitoring monitoring/BaseMonitoring
        :consumer (component/using
                    (consumer/create
                      (setup-queue user-mgmt-handler
                        user-mgmt-queue))
                    [:rmq-connection :monitoring]))
    component/start-system))


(def calc-system
  (-> (component/system-map
        :rmq-connection connection
        :monitoring monitoring/BaseMonitoring
        :consumer (component/using
                    (consumer/create (setup-queue calculation-handler
                                       calculate-queue))
                    [:rmq-connection :monitoring]))
    component/start-system))


(def calc-result-system
  (-> (component/system-map
        :rmq-connection connection
        :monitoring monitoring/BaseMonitoring
        :consumer (component/using
                    (consumer/create (setup-queue calculation-result-handler
                                       calculate-result-queue))
                    [:rmq-connection :monitoring]))
    component/start-system))



;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; let's send some more messages

(pub "some.queue" {:integration_id 234 :message_id "we need this"})
message-received
; it shows!


(pub some-queue {:integration_id 6783 :message_id "and again"})
message-received
; it shows!


(pub user-mgmt-queue {:user "steve" :password "dkktiu-4" :roles [:view-only]})
users


(pub calculate-result-queue {:x 100 :y 200 :result (+ 100 200)})
calculations


(pub calculate-queue {:x 100 :y 200})
calculations


; this test publishing to 1 queue, and looking for the results on a 2nd queue
;
(pub calculate-queue {:x 82 :y 20})
(pub calculate-queue {:x 9 :y 17})
(pub calculate-queue {:x 3 :y 42})
calculations



