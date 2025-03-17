(ns big-config.aero-test
  (:require
   [big-config :as bc]
   [big-config.aero :as aero :refer [read-module]]
   [big-config.lock :as lock]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(deftest read-module-test
  (testing "Read module and resolve `[:big-config.aero/join]"
    (let [opts (-> (read-module {::aero/config (io/resource "aero.edn")
                                 ::aero/module :module-a
                                 ::aero/profile :dev})
                   (select-keys [::lock/working-dir ::lock/run-cmd]))]
      (is (= opts {::lock/working-dir "tofu/251213589273/eu-west-1/tofu.module-a.main"
                   ::lock/run-cmd "bash -c 'cd tofu/251213589273/eu-west-1/tofu.module-a.main && direnv exec . tofu init'"})))))

(deftest read-module-break-loop-test
  (testing "Read module and break the loop"
    (as-> (read-module {::aero/config (io/resource "aero-loop.edn")
                        ::aero/module :module-a}) $
      (::bc/exit $)
      (is (= 1 $)))))
