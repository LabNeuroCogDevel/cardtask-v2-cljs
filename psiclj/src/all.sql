-- :name create-run-table
-- :command :execute
-- :result :raw
-- :doc Create run table
create table if not exists run (
  worker_id  text not null,
  task_name  text not null default "card",
  run_number int not null default 1,
  timepoint int not null default 1,
  version    text not null,
  json       text,
  system_info text,
  created_at timestamp not null default current_timestamp,
  finished_at timestamp,
  -- currently not useful
  --FOREIGN KEY(worker_id) REFERENCES worker(worker_id),
  PRIMARY KEY (worker_id, task_name, version, run_number, timepoint)
)

-- :name create-worker-table
-- :command :execute
-- :result :raw
-- :doc Create worker table
create table worker (
  worker_id  text primary key,
  create_at timestamp not null default current_timestamp
)


--  ":1" result = a single record (hashmap)
-- :name run-by-id :? :1
-- :doc Get all run info by id
select * from run where worker_id = :id and task_name like :task

--  :n returns affected row count
-- :name create-run :! :n
-- :doc create or update response. TODO: add run
replace into run (worker_id, task_name, version, run_number, timepoint, system_info)
values (:id, :task, :version, :run, :timepoint, :info)

-- :name upload-json :! :n
-- :doc create or update response. TODO: add run
update run set json = :json
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint

-- :name upload-system-info :! :n
-- :doc create or update response. TODO: add run
update run set system_info = :info
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint

-- :name finish-run :! :n
-- :doc set finished_at to current time
update run set finished_at = current_timestamp
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint
