(ns big-config.git-test
  (:require
   [big-config.git :as git :refer [check]]
   [big-config.utils-test :refer [test-step-fn]]
   [clojure.test :refer [deftest is testing]]))

(deftest check-test
  (testing "git check test"
    (let [opts {}
          xs (atom [])
          step-fn (partial test-step-fn #{::git/end} xs)]
      (check step-fn opts)
      (is (every? #(= (:exit %) 0) @xs)))))
