(ns big-config.rwl-test
  (:require
   [big-config :as bc]
   [big-config.aero :refer [guardrail-step-fn]]
   [big-config.lock :as lock]
   [big-config.run :as run]
   [big-config.run-with-lock :as rwl :refer [run-with-lock]]
   [big-config.utils-test :refer [default-opts test-step-fn]]
   [clojure.test :refer [deftest is testing]]))

(deftest run-with-lock-test
  (testing "false, conflict, success, different owner"
    (let [opts    default-opts
          xs      (atom [])
          step-fns [(partial test-step-fn #{::rwl/end} xs)]]
      (run-with-lock step-fns (assoc opts ::run/run-cmd "false"))
      (run-with-lock step-fns (assoc opts ::lock/owner "CI2"))
      (run-with-lock step-fns opts)
      (run-with-lock step-fns (assoc opts ::lock/owner "CI2"))
      (as-> @xs $
        (map ::bc/exit $)
        (is (= [1 1 0 0] $))))))

(defn catch-all-step-fn [xs f step opts]
  (swap! xs conj step opts)
  (f step opts))

(deftest step-fn-test
  (testing "the step-fn with step and opts"
    (let [opts    default-opts
          xs      (atom [])
          step-fns [(partial catch-all-step-fn xs)]]
      (run-with-lock step-fns opts)
      (is (= 50 (count @xs)))
      (is (every? (fn [x] (or (keyword? x)
                              (map? x))) @xs)))))

(deftest guardrail-step-fn-test
  (testing "Stop destroying a :prod module"
    (let [expect {:big-config/env :repl, :big-config/err "You cannot destroy a production module", :big-config/exit 1, :big-config/test-mode true, :big-config.aero/module :prod, :big-config.lock/aws-account-id "111111111111", :big-config.lock/fn "invoke" :big-config.lock/lock-keys [:big-config.lock/aws-account-id :big-config.lock/region :big-config.lock/ns :big-config.lock/isolation], :big-config.lock/ns "test.module", :big-config.lock/owner "CI", :big-config.lock/region "eu-west-1", :big-config.run/cmd :destroy, :big-config.run/run-cmd "true"
                  :big-config.lock/isolation (or (System/getenv "ZELLIJ_SESSION_NAME") "CI")}
          actual (->> (run-with-lock [guardrail-step-fn] (merge default-opts
                                                                {:big-config.run/cmd :destroy
                                                                 :big-config.aero/module :prod}))
                      (into (sorted-map)))]
      (is (= expect actual)))))
