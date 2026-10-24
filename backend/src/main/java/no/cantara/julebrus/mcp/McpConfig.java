package no.cantara.julebrus.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Mounts the MCP server at /mcp (Streamable HTTP). Agents list and call the tools in
 * {@link McpTools}. The endpoint sits outside the Google login and the consent gate;
 * {@link McpTokenFilter} is its whole access control.
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpConfig {

    public static final String ENDPOINT = "/mcp";

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransport(ObjectMapper objectMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(ENDPOINT)
                .build();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, McpTools tools,
                                   org.springframework.core.env.Environment env) {
        return McpServer.sync(transport)
                .serverInfo("julebrus", env.getProperty("app.version", "0.1.0"))
                .instructions("Cantara's Christmas beer tasting: history since 1998 and the running season. "
                        + "All tools are read-only. Scores are dice 1-6. Use list_seasons first to see which years exist.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(tools.specifications())
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> bean =
                new ServletRegistrationBean<>(transport, ENDPOINT);
        bean.setName("mcp");
        bean.setAsyncSupported(true);
        bean.setLoadOnStartup(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<McpTokenFilter> mcpTokenFilter(McpProperties props) {
        FilterRegistrationBean<McpTokenFilter> bean = new FilterRegistrationBean<>(new McpTokenFilter(props));
        bean.addUrlPatterns(ENDPOINT, ENDPOINT + "/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.setAsyncSupported(true);
        return bean;
    }
}
