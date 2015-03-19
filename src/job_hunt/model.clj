(ns job-hunt.model)


(def ^:private state
  "The official app state."
  (atom nil))     ; Initialized below with reset-state!


(defn reset-state!
  "For testing. Return the app state to its initial, empty value."
  []
  (reset! state {:last-id 0, :jobs-by-id (sorted-map)}))

(reset-state!)


(defn- insert-job
  "Associates in the given state the given job with a new id, updating
  :last-id with the latter.
  "
  [st job]
  (let [id (inc (:last-id st))
        ]
    (-> st
        (assoc :last-id id)
        (assoc-in [:jobs-by-id id] job))))


(defn now
  "Returns the current time, in milliseconds since the Epoch."
  []
  (.getTime (java.util.Date.)))


(defn redef-now
  "For testing, replaces function \"now\", above, with the given one."
  [new-now-fn]
  (def now new-now-fn))


(defn- fresh?
  "Returns true iff the given job should not expire by the given now-time."
  [nw {ms :last-updated-ms :as job}
   ]
  (< (- nw ms) 60000))


(defn- delete-job-for-id
  "Returns a copy of the given state with the job at the given id removed.
  Used to swap! app state.
  "
  [st id]
  (as-> (:jobs-by-id st) -|-
        (dissoc -|- id)
        (assoc st :jobs-by-id -|-)))


(defn- delete-job-if-old
  "Returns a copy of the given state with the job at the given id removed if
  the job is not fresh. Used to swap! app state.
  "
  [st nw id]
  (if-let [job (get-in st [:jobs-by-id id])
           ]
    (if (fresh? nw job)
      st
      (delete-job-for-id st id))
    st))


(defn new-job
  "Creates a new job with the given \"total\" value. The value is validated
  as a positive integer. Returns the id of the new job, or nil if the value
  is invalid.
  "
  [total]
  (if (and (integer? total) (< 0 total))

    ; Total OK. Update state and return the id of the new job.
    (let [job {:total total, :progress 0, :last-updated-ms (now)}
          ]
      (:last-id (swap! state insert-job job)))

    ; Else, total isn't valid.
    nil))


(defn job-list
  "Returns a vector of id-job pairs, one for each job in the app state. The
  pairs are in order by increasing id. As a side effect, jobs that are not
  fresh are deleted and are not included in the returned vector.
  "
  []
  (let [nw (now)
        ]
    (into [] (:jobs-by-id
      (swap! state update-in [:jobs-by-id] #(->> %
        (filter (comp (partial fresh? nw) second))
        (into (sorted-map))))))))


(defn get-job
  "Returns the job at the given id, or nil if it doesn't exist. As a side
  effect, if the job exists but is not fresh, it is deleted and nil is
  returned.
  "
  [id]
  (swap! state delete-job-if-old (now) id)
  (get-in @state [:jobs-by-id id]))
  ; If no such job for id, returns nil.


(defn delete-job
  "Removes the job at the given id from the app state. Returns the id if it
  was deleted, or nil if it was already absent.
  "
  [id]
  (when (get-job id)      ; OK to inspect job outside of swap!
    ; Even if job disappeared before swap!, delete-job-for-id is OK.
    (swap! state delete-job-for-id id)
    id))
  ; If no such job for id, returns nil.


(defn update-job
  "Updates the job at the given id with the given new \"progress\" value.
  If absolute is truthy, the value replaces the job's existing progress.
  Otherwise, the existing progress will be incremented by the value. Returns
  nil if there is no job with the given id, or the resulting new progress,
  otherwise. As a side effect, if the job exists but is not fresh, it is
  deleted and nil is returned.
  "
  [id new-prog absolute]
  (let [update-progress  (fn [st]
          (if (get-in st [:jobs-by-id id])
            (update-in st [:jobs-by-id id] (fn [job] (-> job
              (assoc :progress (if absolute
                                 new-prog
                                 (+ (:progress job) new-prog)))
              (assoc :last-updated-ms (now)))))
            st))
        ]
    (swap! state (comp update-progress delete-job-if-old) (now) id)
    (:progress (get-in @state [:jobs-by-id id]))))

