package io.github.markpollack.bench.core.benchmark;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootUpgradeBenchmarkTest {

	private final Benchmark benchmark = new BenchmarkCatalog(Path.of("../benchmarks")).discover()
		.stream()
		.filter(candidate -> candidate.name().equals("spring-boot-upgrade"))
		.findFirst()
		.orElseThrow();

	SpringBootUpgradeBenchmarkTest() throws Exception {
	}

	@Test
	void discoversPinnedSpringCloudDeployerTask() {
		assertThat(benchmark.tasks()).singleElement().satisfies(task -> {
			assertThat(task.id()).isEqualTo("spring-cloud-deployer");
			assertThat(task.metadata()).containsEntry("sourceBoot", "2.7.18")
				.containsEntry("targetBoot", "3.2.x");
		});
	}

	@Test
	void initializesTheAlreadyProvidedWorkspaceInsteadOfCloningIntoIt() {
		String setup = benchmark.tasks().getFirst().setup().getFirst();
		assertThat(setup).startsWith("git init")
			.contains("git fetch --depth 1 origin tag v2.9.5")
			.doesNotContain("git clone");
	}

	@Test
	void nativeJuryIsExplicitlyLimitedToTheBuildQuickCheck() {
		assertThat(benchmark.juryConfig()).extractingByKey("tiers").asList().singleElement();
	}

}
