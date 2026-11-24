package com.dsh.agent;

import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;

/** 引擎只依赖这一次调用；生产实现包 :models ModelRouter.execute。 */
public interface LlmCaller {
    ModelResponse call(ModelRequest request);
}
