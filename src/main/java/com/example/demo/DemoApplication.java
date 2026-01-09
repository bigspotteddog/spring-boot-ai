package com.example.demo;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
			chatService.prompt(args[0]);
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
	private final ChatClient chatClient;
	private final ChatModel chatModel;
	private ToolCallbackProvider toolCallbacks;

	public ChatService(ChatClient.Builder chatClientBuilder, ChatModel chatModel, ToolCallbackProvider toolCallbacks) {
		this.chatClient = chatClientBuilder.build();
		this.chatModel = chatModel;
		this.toolCallbacks = toolCallbacks;
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

	public void prompt(String prompt) {
		String modelName = "Unknown";
		if (chatModel instanceof AnthropicChatModel) {
			AnthropicChatModel anthropic = (AnthropicChatModel) chatModel;
			modelName = anthropic.getDefaultOptions().getModel();
		}
		System.out.println("Model: " + modelName);
		var response = chatClient.prompt()
			.user(prompt)
			.toolCallbacks(toolCallbacks)
			.call()
			.content();
		System.out.println(response);
	}
}
