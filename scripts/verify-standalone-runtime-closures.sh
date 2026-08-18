#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
maven_repo=${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}
project_version=$("${repo_root}/mvnw" -q -N -f "${repo_root}/pom.xml" \
  -Dmaven.repo.local="${maven_repo}" help:evaluate -Dexpression=project.version -DforceStdout)
work_dir=$(mktemp -d)
trap 'rm -rf "${work_dir}"' EXIT

version_at_least() {
  local actual=$1
  local minimum=$2
  [[ "$(printf '%s\n%s\n' "${minimum}" "${actual}" | sort -V | head -n 1)" == "${minimum}" ]]
}

assert_present() {
  local list_file=$1
  local coordinate=$2
  if ! grep -Eq "[[:space:]]${coordinate}:jar:" "${list_file}"; then
    echo "Required runtime artifact missing: ${coordinate}" >&2
    return 1
  fi
}

check_jackson_floor() {
  local list_file=$1
  local group=$2
  local minimum=$3
  local line coordinate version
  while IFS= read -r line; do
    coordinate=$(sed -E 's/^[[:space:]]*([^:]+:[^:]+):jar:.*/\1/' <<<"${line}")
    version=$(sed -E 's/^[[:space:]]*[^:]+:[^:]+:jar:([^:[:space:]]+):.*/\1/' <<<"${line}")
    if ! version_at_least "${version}" "${minimum}"; then
      echo "Jackson floor violation: ${coordinate}:${version} is below ${minimum}" >&2
      return 1
    fi
  done < <(grep -E "^[[:space:]]*${group}:(jackson-core|jackson-databind|jackson-dataformat-yaml):jar:" \
    "${list_file}" || true)
}

check_module() {
  local module=$1
  local consumer_dir="${repo_root}/src/it/standalone-${module}"
  local output_dir="${work_dir}/${module}"
  local list_file="${output_dir}/runtime-dependencies.txt"
  mkdir -p "${output_dir}/jars"

  "${repo_root}/mvnw" -B -q -f "${consumer_dir}/pom.xml" \
    -Dmaven.repo.local="${maven_repo}" -Dagent-bench.version="${project_version}" \
    dependency:list -DincludeScope=runtime -Dsort=true -DoutputAbsoluteArtifactFilename=true \
    -DoutputFile="${list_file}"
  "${repo_root}/mvnw" -B -q -f "${consumer_dir}/pom.xml" \
    -Dmaven.repo.local="${maven_repo}" -Dagent-bench.version="${project_version}" \
    dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="${output_dir}/jars"

  if grep -Eq '^[[:space:]]*(org\.testcontainers|com\.github\.docker-java):' "${list_file}"; then
    echo "Container runtime dependency found in agent-bench-${module}:" >&2
    grep -E '^[[:space:]]*(org\.testcontainers|com\.github\.docker-java):' "${list_file}" >&2
    return 1
  fi
  if find "${output_dir}/jars" -type f -name '*docker-java-transport-zerodep*.jar' -print -quit | grep -q .; then
    echo "Shaded Docker transport found in agent-bench-${module}" >&2
    return 1
  fi

  check_jackson_floor "${list_file}" 'com\.fasterxml\.jackson\.(core|dataformat)' '2.21.6'
  check_jackson_floor "${list_file}" 'tools\.jackson\.(core|dataformat)' '3.1.6'
  assert_present "${list_file}" 'io.github.markpollack:agent-judge-core'
  assert_present "${list_file}" 'io.github.markpollack:agent-judge-exec'
  if [[ "${module}" == "agents" ]]; then
    assert_present "${list_file}" 'io.github.markpollack:agent-client-core'
    assert_present "${list_file}" 'io.github.markpollack:agent-claude'
  fi

  cp "${list_file}" "${repo_root}/target/standalone-${module}-runtime-dependencies.txt"
  echo "agent-bench-${module} ${project_version}: standalone runtime closure passed"
}

mkdir -p "${repo_root}/target"
check_module core
check_module agents
