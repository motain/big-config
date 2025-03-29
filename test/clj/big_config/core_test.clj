(ns big-config.core-test
  (:require
   [big-config.core :refer [->step-fn]]
   [clojure.test :refer [deftest is]]))

(deftest ->step-fn-test
  (is (thrown? IllegalArgumentException
               (->step-fn {}))))
