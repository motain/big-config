(ns big-config.lock
  (:require
   [babashka.process :as process]
   [big-config.utils :as utils :refer [default-step-fn generic-cmd handle-cmd
                                       nested-sort-map recur-not-ok-or-end
                                       recur-ok-or-end]]
   [buddy.core.codecs :as codecs]
   [buddy.core.hash :as hash]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defn generate-lock-id [opts]
  (let [{:keys [lock-keys owner]} opts
        lock-details (select-keys opts lock-keys)
        lock-name (-> lock-details
                      nested-sort-map
                      pr-str
                      hash/sha256
                      codecs/bytes->hex
                      str/upper-case
                      (subs 0 4)
                      (as-> $ (str "LOCK-" $)))]
    (-> opts
        (assoc :lock-details lock-details)
        (update :lock-details assoc :owner owner)
        (merge {:lock-name lock-name
                :exit 0
                :err nil}))))

(defn delete-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git tag -d %s" lock-name))))

(defn create-tag [opts]
  (let [{:keys [lock-name
                lock-details]} opts
        proc (-> (process/shell {:continue true
                                 :in (pr-str lock-details)
                                 :out :string
                                 :err :string} (format "git tag -a %s -F -" lock-name)))]
    (handle-cmd opts proc)))

(defn push-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git push origin %s" lock-name))))

(defn delete-remote-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git push --delete origin %s" lock-name))))

(defn get-remote-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git fetch origin tag %s --no-tags" lock-name))))

(defn read-tag [opts]
  (let [{:keys [lock-name]} opts
        cmd (format "git cat-file -p %s" lock-name)]
    (generic-cmd opts cmd :tag-content)))

(defn parse-tag-content [tag-content]
  (->> tag-content
       str/split-lines
       (filter #(str/starts-with? % "{"))
       first
       edn/read-string))

(defn check-tag [opts]
  (let [{:keys [tag-content]} opts
        ownership (every? (fn [[k v]]
                            (= (get opts k) v))
                          (parse-tag-content tag-content))]
    (merge opts (if ownership
                  {:exit 0
                   :err nil}
                  {:exit 1
                   :err "Different owner"}))))

(defn check-remote-tag [opts]
  (let [{:keys [lock-name]} opts
        cmd (format "git ls-remote --exit-code origin  refs/tags/%s" lock-name)
        {:keys [exit] :as opts} (generic-cmd opts cmd)]
    (when (= exit 2)
      (assoc opts :exit 0))))

(defn acquire
  ([opts]
   (acquire opts default-step-fn))
  ([opts step-fn]
   (loop [step :generate-lock-id
          opts opts]
     (case step
       :generate-lock-id (as-> (step-fn {:f generate-lock-id
                                         :step step
                                         :opts opts}) $
                           (recur-ok-or-end :delete-tag $))
       :delete-tag (as-> (step-fn {:f delete-tag
                                   :step step
                                   :opts opts}) $
                     (recur :create-tag $))
       :create-tag (as-> (step-fn {:f create-tag
                                   :step step
                                   :opts opts}) $
                     (recur-ok-or-end :push-tag $))
       :push-tag (as-> (step-fn {:f push-tag
                                 :step step
                                 :opts opts}) $
                   (recur-not-ok-or-end :get-remote-tag $))
       :get-remote-tag (as-> (step-fn {:f (comp get-remote-tag delete-tag)
                                       :step step
                                       :opts opts}) $
                         (recur-ok-or-end :read-tag $))
       :read-tag (as-> (step-fn {:f read-tag
                                 :step step
                                 :opts opts}) $
                   (recur-ok-or-end :check-tag $))
       :check-tag (as-> (step-fn {:f check-tag
                                  :step step
                                  :opts opts}) $
                    (recur :end $))
       :end (step-fn {:f identity
                      :step step
                      :opts opts})))))

(defn release-any-owner
  ([opts]
   (release-any-owner opts default-step-fn))
  ([opts step-fn]
   (loop [step :generate-lock-id
          opts opts]
     (case step
       :generate-lock-id (as-> (step-fn {:f generate-lock-id
                                         :step step
                                         :opts opts}) $
                           (recur :delete-tag $))
       :delete-tag (as-> (step-fn {:f delete-tag
                                   :step step
                                   :opts opts}) $
                     (recur :delete-remote-tag $))
       :delete-remote-tag (as-> (step-fn {:f delete-remote-tag
                                          :step step
                                          :opts opts}) $
                            (recur :check-remote-tag $))
       :check-remote-tag (as-> (step-fn {:f check-remote-tag
                                         :step step
                                         :opts opts}) $
                           (recur :end $))
       :end (step-fn {:f identity
                      :step step
                      :opts opts})))))

(comment)
