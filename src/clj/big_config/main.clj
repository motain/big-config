(ns big-config.main
  (:require
   [babashka.process :as process]
   [big-config.env :refer [env]]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.spec :as bs]
   [big-config.utils :as utils :refer [description-for-step error-for-step]]
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]))

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

(defn run-cmd [opts]
  (let [{:keys [run-cmd]} opts
        res (process/shell {:continue true} run-cmd)]
    (update opts :cmd-results (fnil conj []) res)))

(defn git-push [opts]
  (utils/generic-cmd opts "git push"))

(defn ^:export run-with-lock [opts]
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (loop [step :lock-acquire
         opts opts]
    (println (description-for-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (error-for-step step)]
      (case step
        :lock-acquire (as-> (lock/acquire opts) $
                        (utils/recur-with-no-error :git-check $))
        :git-check (as-> (git/check opts) $
                     (utils/recur-with-no-error :run-cmd $))
        :run-cmd (as-> (run-cmd opts) $
                   (utils/recur-with-no-error :git-push $ error-msg))
        :git-push (as-> (git-push opts) $
                    (utils/recur-with-no-error :lock-release $ error-msg))
        :lock-release (lock/release opts)))))

(comment
  (alter-var-root #'env (constantly :test))
  (create {:aws-account-id "251213589273"
           :region "eu-west-1"
           :ns "tofu.module-a.main"
           :fn "invoke"})
  (let [opts {:aws-account-id "251213589273"
              :region "eu-west-1"
              :ns "tofu.module-a.main"
              :fn "invoke"
              :owner "ALBERTO_MACOS"
              :lock-keys [:aws-account-id :region :ns :owner]
              :run-cmd "true"}] opts))
