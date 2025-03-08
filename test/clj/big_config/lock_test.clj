(ns big-config.lock-test
  (:require
   [big-config.lock :refer [acquire release-any-owner]]
   [big-config.main-test :refer [default-opts]]
   [clojure.test :refer [deftest is testing]]))

(deftest lock-test
  (testing "acquire and release lock"
    (let [opts default-opts
          xs (atom [])
          end-fn (partial swap! xs conj)]
      (acquire opts end-fn)
      (release-any-owner opts end-fn)
      (release-any-owner opts end-fn)
      (is (every? #(= (:exit %) 0) @xs)))))
