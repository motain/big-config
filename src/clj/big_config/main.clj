(ns big-config.main
  (:require
   [big-config.env :refer [env]]
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.spec :as bs]
   [big-config.utils :as utils :refer [starting-step step-failed]]
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

(defn run-tofu-apply [opts]
  (let [{:keys [tofu-apply-cmd]} opts]
    (utils/generic-cmd opts tofu-apply-cmd)))

(defn git-push [opts]
  (utils/generic-cmd opts "git push"))

(defn ^:export tofu-apply [opts]
  (loop [step :lock-acquire
         opts opts]
    (println (starting-step step))
    (let [opts (update opts :steps (fnil conj []) step)
          error-msg (step-failed step)]
      (case step
        :lock-acquire (as-> (lock/acquire opts) $
                        (utils/recur-with-no-error :git-check $))
        :git-check (as-> (git/check opts) $
                     (utils/recur-with-no-error :run-tofu-apply $))
        :run-tofu-apply (as-> (run-tofu-apply opts) $
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
              :tofu-apply-cmd "bash -c 'cd tofu/251213589273/eu-west-1/tofu.module-a.main && direnv exec . tofu apply -auto-approve'"}]
    (loop [step :lock-acquire
           opts opts]
      (println (starting-step step))
      (let [opts (update opts :steps (fnil conj []) step)
            error-msg (step-failed step)]
        (case step
          :lock-acquire (as-> (lock/acquire opts) $
                          (utils/recur-with-no-error :git-check $))
          :git-check (as-> (git/check opts) $
                       (utils/recur-with-no-error :run-tofu-apply $))
          :run-tofu-apply (as-> (run-tofu-apply opts) $
                            (utils/recur-with-no-error :git-push $ error-msg))
          :git-push (as-> (git-push opts) $
                      (utils/recur-with-no-error :lock-release $ error-msg))
          :lock-release (lock/release opts))))))
