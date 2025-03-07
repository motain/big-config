(ns big-config.main-test
  (:require
   [big-config.main :refer [run-with-lock]]
   [clojure.test :refer [deftest is testing]]))

(deftest run-with-lock-with-true-test
  (testing "true"
    (let [opts {:aws-account-id "111111111111"
                :region "eu-west-1"
                :ns "test.module"
                :fn "invoke"
                :owner "CI"
                :lock-keys [:aws-account-id :region :ns]
                :run-cmd "true"}
          xs (atom [])
          end-fn (partial swap! xs conj)]
      (run-with-lock opts end-fn)
      (is (every? #(= (:exit %) 0) @xs)))))

(deftest run-with-lock-with-false-and-conflict-test
  (testing "false and conflict"
    (let [opts   {:aws-account-id "111111111111"
                  :region         "eu-west-1"
                  :ns             "test.module"
                  :fn             "invoke"
                  :owner          "CI"
                  :lock-keys      [:aws-account-id :region :ns]
                  :run-cmd        "false"}
          xs     (atom [])
          end-fn (partial swap! xs conj)]
      (run-with-lock opts end-fn)
      (run-with-lock (assoc opts :owner "CI2") end-fn)
      (run-with-lock (assoc opts :run-cmd "true") end-fn)
      (as-> @xs $
        (map :exit $)
        (is (= [1 1 0] $))))))
