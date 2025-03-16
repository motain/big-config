(ns big-config.utils-test
  (:require
   [big-config :as bc]
   [big-config.utils :refer [default-step-fn]]))

(defn test-step-fn [end-steps xs {:keys [f step opts]}]
  (when (end-steps step)
    (swap! xs conj opts))
  (default-step-fn {:f f
                    :step step
                    :opts opts}))

(def default-opts #:big-config.lock {:aws-account-id "111111111111"
                                     :region "eu-west-1"
                                     :ns "test.module"
                                     :fn "invoke"
                                     :owner "CI"
                                     :lock-keys [:big-config.lock/aws-account-id
                                                 :big-config.lock/region
                                                 :big-config.lock/ns]
                                     :run-cmd "true"
                                     ::bc/test-mode true
                                     ::bc/env :repl})
