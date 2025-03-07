(ns big-config.main-test
  (:require
   [big-config.main :refer [run-with-lock]]
   [clojure.test :refer [deftest is testing]]))

(def default-opts {:aws-account-id "111111111111"
                   :region "eu-west-1"
                   :ns "test.module"
                   :fn "invoke"
                   :owner "CI"
                   :lock-keys [:aws-account-id :region :ns]
                   :run-cmd "true"})

(deftest run-with-lock-test
  (testing "false, conflict, success, different owner"
    (let [opts   default-opts
          xs     (atom [])
          end-fn (partial swap! xs conj)]
      (run-with-lock (assoc opts :run-cmd "false") end-fn)
      (run-with-lock (assoc opts :owner "CI2") end-fn)
      (run-with-lock opts end-fn)
      (run-with-lock (assoc opts :owner "CI2") end-fn)
      (as-> @xs $
        (map :exit $)
        (is (= [1 1 0 0] $))))))
