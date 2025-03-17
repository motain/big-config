(ns big-config.rwl-test
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.run-with-lock :as rwl :refer [run-with-lock]]
   [big-config.utils-test :refer [test-step-fn]]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(defn load-opts [resource]
  {::aero/config (io/resource resource)
   ::aero/module :module-a})

(deftest run-with-lock-test
  (testing "false, conflict, success, different owner"
    (let [xs      (atom [])
          step-fn (partial test-step-fn #{::rwl/end} xs)]
      (run-with-lock step-fn (load-opts "rwl.edn"))
      (run-with-lock step-fn (load-opts "rwl-2.edn"))
      (run-with-lock step-fn (load-opts "rwl-3.edn"))
      (run-with-lock step-fn (load-opts "rwl-4.edn"))
      (as-> @xs $
        (map ::bc/exit $)
        (is (= [1 1 0 0] $))))))

(defn catch-all-step-fn [xs {:keys [f step opts]}]
  (swap! xs conj step opts)
  (f opts))

(deftest step-fn-test
  (testing "the step-fn with step and opts"
    (let [opts    (load-opts "rwl-4.edn")
          xs      (atom [])
          step-fn (partial catch-all-step-fn xs)]
      (run-with-lock step-fn opts)
      (is (= 52 (count @xs)))
      (is (every? (fn [x] (or (keyword? x)
                              (map? x))) @xs)))))
