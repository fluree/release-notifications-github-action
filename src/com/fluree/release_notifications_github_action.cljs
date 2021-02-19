(ns com.fluree.release-notifications-github-action
  (:require ["@actions/core" :as actions]
            ["@octokit/core" :refer [Octokit]]
            [clojure.string :as str]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [oget]]))

(defn parse-repo [repo]
  (let [[owner repo] (str/split repo #"/")]
    {:owner owner, :repo repo}))

(defn this-repo []
  (->> "GITHUB_REPOSITORY"
       (oget (.-env js/process))
       parse-repo))

(defn canonical-repo [repo]
  (if (str/includes? repo "/")
    (parse-repo repo)
    (assoc (this-repo) :repo repo)))

(defn repo->event-type [{:keys [owner repo]}]
  (str owner "-" repo "-release"))

(defn new-octokit [access-token]
  (Octokit. #js {:auth access-token}))

(def octokit
  (-> "token"
      actions/getInput
      new-octokit
      delay))

(defn dispatch-event [{:keys [owner repo] :as full-repo}]
  (.request @octokit
    "POST /repos/{owner}/{repo}/dispatches"
    #js {:owner      owner
         :repo       repo
         :event_type (repo->event-type full-repo)}))

(defn notify-repo [repo]
  (println "Notifying repo:" repo)
  (go
    (let [resp (<p! (dispatch-event repo))]
      (when-not (= 204 (oget resp "status"))
        (actions/setFailed (str "Repository dispatch event response was" (pr-str resp)))))))

(defn run [repos]
  (println "Notifying" (count repos) "repos")
  (run! (comp notify-repo canonical-repo) repos))

(try
  (let [repos (-> "repos-to-notify" actions/getInput js/JSON.parse)]
    (run repos))
  (catch js/Error e
    (actions/setFailed (.-message e))))
