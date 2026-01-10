package com.example.demo;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

public class DemoApplication {
	public static void main(String[] args) {
		if (args.length > 0) {
			var app = new SpringApplication(DemoApplicationBoot.class);
			app.setDefaultProperties(java.util.Map.of("spring.shell.interactive.enabled", "false"));
			var context = app.run();
			var chatService = context.getBean(ChatService.class);
			var prompt = args[0];
			var model = args.length > 1 ? args[1] : null;
			chatService.prompt(prompt, model);
			System.exit(0);
		}
		SpringApplication.run(DemoApplicationBoot.class, args);
	}
}

@SpringBootApplication
class DemoApplicationBoot {
	@Bean
	public ApplicationRunner printTools(ChatService chatService) {
		return args -> chatService.printTools();
	}

}

@Component
class ChatService {
	private final ChatClient anthropicChatClient;
	private final ChatClient openAiChatClient;
	private ToolCallbackProvider toolCallbacks;

	public ChatService(
			AnthropicChatModel anthropicModel,
			org.springframework.beans.factory.ObjectProvider<OpenAiChatModel> openAiModelProvider,
			java.util.Optional<ToolCallbackProvider> toolCallbacks) {
		this.toolCallbacks = toolCallbacks.orElse(null);
		
		// Pre-instantiate ChatClients with cleared default options to allow per-request overrides
		this.anthropicChatClient = ChatClient.builder(anthropicModel)
			.defaultOptions(ChatOptions.builder().build())
			.build();
		
		// Try to get OpenAI ChatClient, fallback if not configured
		OpenAiChatModel openAiModel = openAiModelProvider.getIfAvailable();
		this.openAiChatClient = openAiModel != null 
			? ChatClient.builder(openAiModel)
				.defaultOptions(OpenAiChatOptions.builder().build())
				.build()
			: null;
	}

	public void printTools() {
		System.out.println("\nAvailable MCP Tools:");
		if (toolCallbacks != null) {
			var callbacks = toolCallbacks.getToolCallbacks();
			for (var callback : callbacks) {
				var definition = callback.getToolDefinition();
				System.out.println("  - " + definition.name() + ": " + definition.description());
			}
		} else {
			System.out.println("  (No tools available)");
		}
		System.out.println();
	}

	public void prompt(String prompt, String modelOverride) {
		// Determine which ChatClient to use
		ChatClient chatClient;
		String modelName;
		
		if (modelOverride != null && isOpenAiModel(modelOverride)) {
			if (openAiChatClient == null) {
				throw new IllegalArgumentException("OpenAI model not available. Set spring.ai.openai.api-key and ensure OpenAiChatModel is configured.");
			}
			chatClient = openAiChatClient;
			modelName = modelOverride;
		} else {
			chatClient = anthropicChatClient;
			modelName = modelOverride != null ? modelOverride : "claude-haiku-4-5";
		}
		
		System.out.println("Model: " + modelName);
		
		var request = chatClient.prompt()
			.user(prompt);
		
		if (toolCallbacks != null) {
			request = request.toolCallbacks(toolCallbacks);
		}
		
		// Set model name in options, explicitly set temperature to 1.0 for OpenAI (models have different constraints)
		if (isOpenAiModel(modelName)) {
			request = request.options(OpenAiChatOptions.builder()
				.model(modelName)
				.temperature(1.0)
				.build());
		} else {
			request = request.options(ChatOptions.builder().model(modelName).build());
		}
		
		var response = request.call().content();
		System.out.println(response);
	}
	

	
	private boolean isOpenAiModel(String modelName) {
		return modelName.contains("gpt-") || modelName.startsWith("o1-") || modelName.startsWith("o3-");
	}
	
	private boolean isAnthropicModel(String modelName) {
		return modelName.contains("claude-");
	}
}
