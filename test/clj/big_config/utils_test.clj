(ns big-config.utils-test)

(defn test-step-fn [xs {:keys [f step opts]}]
  (when (= step :end)
    (swap! xs conj opts))
  (f opts))

(def default-opts {:aws-account-id "111111111111"
                   :region "eu-west-1"
                   :ns "test.module"
                   :fn "invoke"
                   :owner "CI"
                   :lock-keys [:aws-account-id :region :ns]
                   :run-cmd "true"
                   :env :repl})
