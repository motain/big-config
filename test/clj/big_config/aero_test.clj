(ns big-config.aero-test
  (:require
   [big-config.aero :as aero :refer [read-module]]
   [big-config.lock :as lock]
   [clojure.test :refer [deftest is testing]]))

(deftest read-module-test
  (testing "Read module and resolve [:big-config.aero/join]"
    (let [opts (-> (read-module {::aero/resource "aero.edn"
                                 ::aero/module :module-a
                                 ::aero/profile :dev})
                   (select-keys [::lock/working-dir ::lock/run-cmd]))]
      (is (= opts {::lock/working-dir "tofu/251213589273/eu-west-1/tofu.module-a.main"
                   ::lock/run-cmd "bash -c 'cd tofu/251213589273/eu-west-1/tofu.module-a.main && direnv exec . tofu init'"})))))
