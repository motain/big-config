(ns big-config.utils-test
  (:require
   [big-config.utils :refer [default-step-fn]]))

(defn test-step-fn [end-step xs {:keys [f step opts]}]
  (when (= step end-step)
    (swap! xs conj opts))
  (default-step-fn {:f f
                    :step step
                    :opts opts}))

(def default-opts {:aws-account-id "111111111111"
                   :region "eu-west-1"
                   :ns "test.module"
                   :fn "invoke"
                   :owner "CI"
                   :lock-keys [:aws-account-id :region :ns]
                   :run-cmd "true"
                   :env :repl})
