(ns big-config.lock-test
  (:require
   [big-config.lock :as lock :refer [lock]]
   [big-config.unlock :as unlock :refer [unlock-any]]
   [big-config.utils-test :refer [default-opts test-step-fn]]
   [clojure.test :refer [deftest is testing]]))

(deftest lock-test
  (testing "acquire and release lock"
    (let [opts default-opts
          xs (atom [])
          step-fn (partial test-step-fn #{::lock/end ::unlock/end} xs)]
      (lock step-fn opts)
      (unlock-any step-fn opts)
      (unlock-any step-fn opts)
      (is (= 3 (count @xs)))
      (is (every? #(= (:exit %) 0) @xs)))))
