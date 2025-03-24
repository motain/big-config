(ns big-config.aero-test
  (:require
   [big-config :as bc]
   [big-config.aero :as aero :refer [read-module]]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(deftest read-module-test
  (testing "Read module and resolve `[:big-config.aero/join]"
    (let [expect {:big-config.lock/owner "ALBERTO_MACOS", :big-config.tofu/region "eu-west-1", :big-config.run/dir "tofu/251213589273/alpha", :big-config.tofu/ns "tofu.module-a.main", :big-config.aero/module :alpha, :big-config.run/shell-opts {:extra-var {"AWS_PROFILE" "251213589273"}, :dir "tofu/251213589273/alpha"}, :big-config.lock/lock-keys [:big-config.tofu/aws-account-id :big-config.tofu/region :big-config.tofu/ns], :big-config.tofu/aws-account-id "251213589273", :big-config/exit 0, :big-config/err nil, :big-config.tofu/fn "invoke", :big-config.aero/profile :dev}
          actual (read-module {::aero/config (io/resource "aero.edn")
                               ::aero/module :alpha
                               ::aero/profile :dev})
          actual (apply dissoc actual [:big-config.aero/config])]
      (is (= expect actual)))))

(deftest read-module-break-loop-test
  (testing "Read module and break the loop"
    (as-> (read-module {::aero/config (io/resource "aero-loop.edn")
                        ::aero/module :module-a}) $
      (::bc/exit $)
      (is (= 1 $)))))

