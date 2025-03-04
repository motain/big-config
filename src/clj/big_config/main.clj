(ns big-config.main
  (:require
   [big-config.spec :as bs]
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]))

(def env :prod)

(defn print-and-flush
  [res]
  (if (= env :prod)
    (do
      (println res)
      (flush))
    res))

(defn ^:export create [args]
  {:pre [(s/valid? ::bs/create args)]}
  (let [{:keys [fn ns]} args]
    (-> (format "%s/%s" ns fn)
        (symbol)
        requiring-resolve
        (apply (vector args))
        (json/generate-string {:pretty true})
        print-and-flush)))

(comment
  (alter-var-root #'env (constantly :test))
  (create {:aws-account-id "251213589273"
           :region "eu-west-1"
           :ns "tofu.module-a.main"
           :fn "invoke"}))
