/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.bench.core.exec.sandbox;

import java.io.IOException;
import java.nio.file.Path;
import io.github.markpollack.bench.core.exec.ExecResult;
import io.github.markpollack.bench.core.exec.ExecSpec;
import io.github.markpollack.bench.core.exec.TimeoutException;

public interface Sandbox extends AutoCloseable {

	ExecResult exec(ExecSpec spec) throws IOException, InterruptedException, TimeoutException;

	Path workDir();

	boolean isClosed();

	@Override
	void close() throws IOException;

}
