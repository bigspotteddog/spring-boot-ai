package com.example.demo;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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
		// Use OpenAI if requested
		if (modelOverride != null && isOpenAiModel(modelOverride)) {
			if (openAiChatClient == null) {
				throw new IllegalArgumentException("OpenAI model not available. Set spring.ai.openai.api-key and ensure OpenAiChatModel is configured.");
			}
			promptOpenAi(prompt, modelOverride);
		} else {
			// Default to Anthropic
			String modelName = modelOverride != null ? modelOverride : "claude-haiku-4-5";
			promptAnthropic(prompt, modelName);
		}
	}
	
	private void promptOpenAi(String prompt, String modelName) {
		System.out.println("Model: " + modelName);
		
		var request = openAiChatClient.prompt()
			.user(prompt);
		
		if (toolCallbacks != null) {
			request = request.toolCallbacks(toolCallbacks);
		}
		
		// OpenAI: Set model and temperature (1.0 is safe for gpt-* models)
		request = request.options(OpenAiChatOptions.builder()
			.model(modelName)
			.temperature(1.0)
			.build());
		
		var response = request.call().content();
		System.out.println(response);
	}
	
	private void promptAnthropic(String prompt, String modelName) {
		System.out.println("Model: " + modelName);
		
		var request = anthropicChatClient.prompt()
			.user(prompt);
		
		if (toolCallbacks != null) {
			request = request.toolCallbacks(toolCallbacks);
		}
		
		// Anthropic: Set model only (supports various temperature values via other options)
		request = request.options(ChatOptions.builder().model(modelName).build());
		
		var response = request.call().content();
		System.out.println(response);
	}

	private boolean isOpenAiModel(String modelName) {
		return modelName.contains("gpt-") || modelName.startsWith("o1-") || modelName.startsWith("o3-");
	}	
}
