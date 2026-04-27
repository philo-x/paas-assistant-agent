package io.agentscope.examples.paasassistant.platform.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds a Kubernetes client using incluster or kubeconfig authentication.
 */
@Configuration
public class KubernetesClientConfig {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient(
            @Value("${platform.k8s.auth-mode:incluster}") String authMode,
            @Value("${platform.k8s.kubeconfig-path:${user.home}/.kube/config}") String kubeconfigPath,
            @Value("${platform.k8s.context:}") String context)
            throws IOException {
        Config config;
        if ("kubeconfig".equalsIgnoreCase(authMode)) {
            String kubeconfig = Files.readString(Path.of(kubeconfigPath));
            if (StringUtils.hasText(context)) {
                config = Config.fromKubeconfig(context, kubeconfig, kubeconfigPath);
            } else {
                config = Config.fromKubeconfig(kubeconfig);
            }
        } else {
            config = Config.autoConfigure(StringUtils.hasText(context) ? context : null);
        }
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
