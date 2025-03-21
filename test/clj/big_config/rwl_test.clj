(ns big-config.rwl-test
  (:require
   [big-config :as bc]
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
