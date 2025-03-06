(ns big-config.lock-test
  (:require
   [big-config.lock :refer [acquire release-any-owner]]
   [clojure.test :refer [deftest is testing]]))

(deftest lock-test
  (testing "acquire and release lock"
    (let [opts {:aws-account-id "111111111111"
                :region "eu-west-1"
                :ns "test.module"
                :fn "invoke"
                :owner "CI"
                :lock-keys [:aws-account-id :region :ns]}
          xs (atom [])
          end-fn (partial swap! xs conj)]
      (acquire opts end-fn)
      (release-any-owner opts end-fn)
      (is (every? #(= (:exit %) 0) @xs)))))
