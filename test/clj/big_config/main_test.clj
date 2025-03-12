(ns big-config.main-test
  (:require
   [big-config.main :refer [run-with-lock]]
   [big-config.utils-test :refer [default-opts test-step-fn]]
   [clojure.test :refer [deftest is testing]]))

(deftest run-with-lock-test
  (testing "false, conflict, success, different owner"
    (let [opts    default-opts
          xs      (atom [])
          step-fn (partial test-step-fn xs)]
      (run-with-lock (assoc opts :run-cmd "false") step-fn)
      (run-with-lock (assoc opts :owner "CI2") step-fn)
      (run-with-lock opts step-fn)
      (run-with-lock (assoc opts :owner "CI2") step-fn)
      (as-> @xs $
        (map :exit $)
        (is (= [1 1 0 0] $))))))

(defn catch-all-step-fn [xs {:keys [f step opts]}]
  (swap! xs conj step opts)
  (f opts))

(deftest step-fn-test
  (testing "the step-fn with step and opts"
    (let [opts    default-opts
          xs      (atom [])
          step-fn (partial catch-all-step-fn xs)]
      (run-with-lock opts step-fn)
      (is (= 12 (count @xs)))
      (is (every? (fn [x] (or (keyword? x)
                              (map? x))) @xs)))))
