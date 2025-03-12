(ns big-config.lock-test
  (:require
   [big-config.lock :refer [acquire release-any-owner]]
   [big-config.utils-test :refer [default-opts test-step-fn]]
   [clojure.test :refer [deftest is testing]]))

(deftest lock-test
  (testing "acquire and release lock"
    (let [opts default-opts
          xs (atom [])
          step-fn (partial test-step-fn xs)]
      (acquire opts step-fn)
      (release-any-owner opts step-fn)
      (release-any-owner opts step-fn)
      (is (every? #(= (:exit %) 0) @xs)))))
