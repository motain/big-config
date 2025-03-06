(ns big-config.main-test
  (:require
   [big-config.main :refer [run-with-lock]]
   [clojure.test :refer [deftest is testing]]))

(deftest run-with-lock-test
  (testing "true"
    (let [opts {:aws-account-id "251213589273"
                :region "eu-west-1"
                :ns "tofu.module-a.main"
                :fn "invoke"
                :owner "ALBERTO_MACOS"
                :lock-keys [:aws-account-id :region :ns]
                :run-cmd "true"}
          xs (atom [])
          end-fn (partial swap! xs conj)]
      (run-with-lock opts end-fn)
      (is (every? #(= (:exit %) 0) @xs)))))
