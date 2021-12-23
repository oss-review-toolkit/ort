CREATE TABLE IF NOT EXISTS ort_result(
    id SERIAL PRIMARY KEY,
    analysis_start_time TIMESTAMP NOT NULL,
    vcs_type TEXT NOT NULL,
    vcs_url TEXT NOT NULL,
    vcs_path TEXT NOT NULL,
    vcs_revision TEXT NOT NULL,
    CONSTRAINT ort_result_uq UNIQUE (
        analysis_start_time,
        vcs_type,
        vcs_url,
        vcs_revision,
        vcs_path
    )
);

CREATE TABLE IF NOT EXISTS package (
    id SERIAL PRIMARY KEY,
    c_type TEXT NOT NULL,
    c_namespace TEXT NOT NULL,
    c_name TEXT NOT NULL,
    c_version TEXT NOT NULL,
    authors TEXT[] NOT NULL,
    declared_licenses TEXT[] NOT NULL,
    description TEXT NOT NULL,
    homepage_url TEXT NOT NULL,
    vcs_type TEXT NOT NULL,
    vcs_url TEXT NOT NULL,
    vcs_revision TEXT NOT NULL,
    vcs_path TEXT NOT NULL,
    source_artifact_url TEXT NOT NULL,
    binary_artifact_url TEXT NOT NULL,
    is_meta_data_only BOOL NOT NULL
);

CREATE OR REPLACE FUNCTION arr_hash(TEXT[]) RETURNS TEXT as
$$ SELECT md5(array_to_string($1,',')); $$
LANGUAGE SQL IMMUTABLE;

CREATE UNIQUE INDEX package_uq_idx ON package (
	c_type,
	c_namespace,
	c_name,
	c_version,
	arr_hash(authors),
	arr_hash(declared_licenses),
	description,
	homepage_url,
	vcs_type,
	vcs_url,
	vcs_revision,
	vcs_path,
	source_artifact_url,
	binary_artifact_url,
	is_meta_data_only
);


CREATE TABLE IF NOT EXISTS ort_result_label (
    ort_result_id INTEGER NOT NULL REFERENCES ort_result(id) ON DELETE CASCADE,
    k TEXT NOT NULL,
    v TEXT NOT NULL,
    PRIMARY KEY(ort_result_id, k, v)
);

CREATE TABLE IF NOT EXISTS ort_result_has_package (
    ort_result_id INTEGER NOT NULL REFERENCES ort_result(id) ON DELETE CASCADE,
    package_id INTEGER NOT NULL REFERENCES package(id) ON DELETE CASCADE,
    is_excluded BOOLEAN NOT NULL,
    PRIMARY KEY(ort_result_id, package_id)
);

CREATE TABLE IF NOT EXISTS ort_result_label (
    ort_result_id INTEGER NOT NULL REFERENCES ort_result(id) ON DELETE CASCADE,
    k TEXT NOT NULL,
    v TEXT NOT NULL,
    PRIMARY KEY (ort_result_id, k, v)
);

CREATE TABLE IF NOT EXISTS ort_result_nested_repository (
    ort_result_id INTEGER NOT NULL REFERENCES ort_result(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    vcs_url TEXT NOT NULL,
    PRIMARY KEY (ort_result_id, path, vcs_url)
);