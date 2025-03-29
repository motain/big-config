(ns big-config.tofu-test
  (:require
   [babashka.process :as process]
   [clojure.test :refer [deftest is testing]]))

(deftest block-destroy-prod-step-fn-test
  (testing "block-destroy-prod-step-fn is stopping from destroying a prod module"
    (let [expect [1 "[38;5;196;1m You cannot destroy module :alpha in :prod[0;m\nerror: Recipe `tofu` failed with exit code 1\n"]
          proc (process/shell {:continue true
                               :out :string
                               :err :string} "just tofu destroy alpha prod")
          {:keys [exit err]} proc
          actual [exit err]]
      (is (= expect actual)))))
