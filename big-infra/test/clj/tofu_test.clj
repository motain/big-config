(ns tofu-test
  (:require
   [aero.core :refer [read-config]]
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.call :as call]
   [big-config.run :as run]
   [clojure.test :refer [deftest is testing]]
   [clojure.walk :as walk]
   [tofu.aero-readers :refer [modules]]))

(defn dynamic-modules []
  (reset! modules #{})
  (read-config "big-config.edn")
  @modules)

(deftest main-stability-test
  (testing "checking if all dynamic files committed are equal to the test generated ones"
    (doall (for [module (dynamic-modules)]
             (let [opts {::aero/config "big-config.edn"
                         ::aero/module module
                         ::aero/profile :prod
                         ::run/dir [:big-config.aero/join
                                    "tofu/"
                                    :big-config.tofu/aws-account-id "/"
                                    :big-config.aero/module]}
                   {:keys [::bc/err
                           ::bc/exit] :as opts} (aero/read-module opts)
                   _ (is (= [0 nil] [exit err]))
                   files (-> opts
                             ::call/fns
                             (->> (filter #(= (:f %) "big-config.call/mkdir-and-spit"))
                                  (map #(-> % :args first :out))))
                   files-v1 (mapv #(slurp %) files)
                   {:keys [::bc/err
                           ::bc/exit]} (call/call-fns opts)
                   _ (is (= [0 nil] [exit err]))
                   files-v2 (mapv #(slurp %) files)
                   _ (is (= files-v1 files-v2) (format "Module %s has changed" module))])))))

(defn catch-nils [opts module]
  (->> opts
       (walk/prewalk (fn [e]
                       (when (nil? e)
                         (throw (ex-info (format "nil value found in module %s" module) {:module module})))
                       e))))

(deftest catch-nils-test
  (testing "checking that the map doesn't contain nils"
    (doall (for [module (dynamic-modules)]
             (let [opts {::aero/config "big-config.edn"
                         ::aero/module module
                         ::aero/profile :prod
                         ::run/dir [:big-config.aero/join
                                    "tofu/"
                                    :big-config.tofu/aws-account-id "/"
                                    :big-config.aero/module]}
                   {:keys [::bc/err
                           ::bc/exit] :as opts} (aero/read-module opts)
                   _ (is (= [0 nil] [exit err]))
                   xs (-> opts
                          ::call/fns
                          (->> (filter #(= (:f %) "big-config.call/mkdir-and-spit"))
                               (filter #(-> % :args first :type (= :json)))
                               (map #(-> % :args first))))]
               (doall (for [x xs]
                        (let [{:keys [f args]} x
                              f (-> f symbol requiring-resolve)]
                          (-> (apply f args)
                              (catch-nils module))))))))))
