(ns big-config.git-test
  (:require [big-config.git :refer [check]]
            [clojure.test :refer [deftest is testing]]))

(deftest check-test
  (testing "acquire and release lock"
    (let [opts {}
          xs (atom [])
          end-fn (partial swap! xs conj)]
      (check opts end-fn)
      (is (every? #(= (:exit %) 0) @xs)))))
