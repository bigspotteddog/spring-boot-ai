# spring-boot-ai

This repo demonstrates a spring ai command line prompt runner with the ability to fetch urls from the internet and read and write files to the configured folders.

The default ai configuration is:

```
  ai:
    model:
      chat: anthropic
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:        
          model: claude-haiku-4-5
          temperature: 0.1
          max-tokens: 4000
          cache-strategy: SYSTEM_AND_TOOLS
    mcp:
      client:
        enabled: true
        type: SYNC
        auto-init: true
        request-timeout: 60s  # Increase timeout        
        toolcallback:
          enabled: true
        stdio:
          connections:
            filesystem:
              command: npx
              args:
                - "-y"
                - "@modelcontextprotocol/server-filesystem"
                - "/Users/bob/Documents/projects"
            fetch:
              command: uvx
              args:
                - "mcp-server-fetch"
```

Run with:

```
% java -jar target/demo-0.0.1-SNAPSHOT.jar "<prompt>"
```

Example for getting the title of an html doc:

```
% java -jar target/demo-0.0.1-SNAPSHOT.jar \
  "Print the title for https://sonatype.com. Keep looking until you find the title tag."
```

Why do I have to tell it to keep looking? The fetch mcp fetches in chunks or 5,000 characters by default. If what you are looking for is farther down than that it might not be found unless the LLM decides on its own to look farther into the doc.
