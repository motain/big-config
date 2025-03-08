(ns big-config.main
  (:require
   [big-config.git :as git]
   [big-config.lock :as lock]
   [big-config.spec :as bs]
   [big-config.utils :refer [exit-end-fn generic-cmd print-and-flush
                             println-step-fn recur-ok-or-end run-cmd]]
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]))

(defn ^:export create [args]
  {:pre [(s/valid? ::bs/create args)]}
  (let [{:keys [fn ns]} args]
    (-> (format "%s/%s" ns fn)
        (symbol)
        requiring-resolve
        (apply (vector args))
        (json/generate-string {:pretty true})
        print-and-flush)))

(defn git-push [opts]
  (generic-cmd opts "git push"))

(defn ^:export acquire-lock [opts]
  (println-step-fn :lock-acquire)
  (lock/acquire opts (partial exit-end-fn "Failed to acquire the lock")))

(defn ^:export release-lock-any-owner [opts]
  (println-step-fn :lock-release-any-owner)
  (lock/release-any-owner opts))

(defn run-with-lock
  ([opts]
   (run-with-lock opts identity))
  ([opts end-fn]
   (run-with-lock opts end-fn (fn [_])))
  ([opts end-fn step-fn]
   #_{:clj-kondo/ignore [:loop-without-recur]}
   (loop [step :lock-acquire
          opts opts]
     (step-fn step)
     (let [opts (update opts :steps (fnil conj []) step)]
       (case step
         :lock-acquire (as-> (lock/acquire opts) $
                         (recur-ok-or-end :git-check $ "Failed to acquire the lock"))
         :git-check (as-> (git/check opts) $
                      (recur-ok-or-end :run-cmd $ "The working directory is not clean"))
         :run-cmd (as-> (run-cmd opts) $
                    (recur-ok-or-end :git-push $ "The command executed with the lock failed"))
         :git-push (as-> (git-push opts) $
                     (recur-ok-or-end :lock-release-any-owner $))
         :lock-release-any-owner (as-> (lock/release-any-owner opts) $
                                   (recur-ok-or-end :end $ "Failed to release the lock"))
         :end (end-fn opts))))))

(defn ^:export run-with-lock! [opts]
  (run-with-lock opts exit-end-fn println-step-fn))

(comment
  (create {:aws-account-id "251213589273"
           :region "eu-west-1"
           :ns "tofu.module-a.main"
           :fn "invoke"}))
