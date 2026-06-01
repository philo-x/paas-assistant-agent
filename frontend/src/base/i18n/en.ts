/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export default {
  common: {
    confirm: 'Confirm',
    cancel: 'Cancel',
    save: 'Save',
    delete: 'Delete',
    edit: 'Edit',
    add: 'Add',
    search: 'Search',
    loading: 'Loading...',
    error: 'Error',
    success: 'Success',
    warning: 'Warning',
    info: 'Info',
    back: 'Back',
    reset: 'Reset',
    refresh: 'Refresh',
    view: 'View',
    close: 'Close',
    set: 'Set',
    language: 'Language',
    chinese: '中文',
    english: 'English'
  },
  home: {
    title: 'PaaS Platform Agent Assistant',
    subtitle: 'Kubernetes Diagnosis and Guidance',
    description: 'Welcome to the PaaS Platform Agent Assistant. You can diagnose K8s issues, explain resources, understand YAML fields, and get command recommendations.',
    startChat: 'Start Chat',
    systemSettings: 'Settings',
    features: {
      title: 'Service Features',
      consult: 'Resource Guidance',
      consultDesc: 'Field explanations, describe interpretation, and YAML guidance',
      order: 'Incident Diagnosis',
      orderDesc: 'Read-first diagnosis for Pods, Deployments, Services, and other core resources',
      feedback: 'Root Cause Analysis',
      feedbackDesc: 'Analyze timeline and blast radius to output confidence levels and risk warnings',
      support: 'Diagnostic SOPs',
      supportDesc: 'Built-in troubleshooting SOPs for 7 core resources based on decision trees'
    },
    cta: {
      title: 'Ready to start a platform diagnosis or consultation?',
      description: 'Click below to open the PaaS Platform Agent Assistant'
    }
  },
  chat: {
    mode: {
      flash: "Quick Scan",
      pro: "Deep Diagnosis",
      flashDesc: "Focus on resource lookup and quick scanning, fast response",
      proDesc: "Focus on application and resource diagnosis and root-cause analysis, in-depth reasoning"
    },
    title: 'PaaS Platform Agent Assistant',
    placeholder: {
      flash: 'Enter questions about resource lookup or quick scan, e.g., what Pods are in the kube-system namespace?',
      pro: 'Enter problems requiring deep diagnosis, e.g., diagnose Deployment coredns in the kube-system namespace'
    },
    send: 'Send',
    clear: 'Clear Chat',
    settings: 'Settings',
    thinking: 'Agent is working...',
    error: 'Send failed, please try again',
    sendError: 'Send failed',
    unknownError: 'Unknown error',
    welcome: 'Hello! I am the PaaS Platform Agent Assistant. I can help with K8s diagnosis, resource explanation, command recommendations, and resource guidance.',
    chatCleared: 'Chat cleared',
    sessionId: 'Session ID',
    userId: 'User ID',
    clusterId: 'Cluster ID',
    setUserId: 'Set User ID',
    setClusterId: 'Set Cluster ID',
    userIdSetSuccess: 'User ID set successfully',
    clusterSetSuccess: 'Cluster ID set successfully',
    userIdRequired: 'Please enter a valid user ID',
    clusterRequired: 'Please enter a valid cluster ID',
    userIdPrompt: 'Please enter your user ID for approval and audit tracking:',
    clusterPrompt: 'Please select the target Kubernetes cluster environment to proceed:',
    userIdPlaceholder: 'Enter platform user ID',
    clusterPlaceholder: 'Select cluster environment',
    tooltip: {
      noBaseUrlAndUserId: 'Please set the backend URL and user ID in settings',
      noBaseUrl: 'Please set the backend URL in settings',
      noUserId: 'Please set the user ID in settings',
      noClusterId: 'Please set the cluster ID first'
    },
    sections: {
      answer: 'Final Answer',
      tools: 'Tool Calls',
      reasoning: 'Agent Reasoning',
      thinking: 'Thinking',
      thoughts: 'Thoughts',
      conclusion: 'Final Conclusion',
      result: 'Agent'
    },
    showTools: 'Show',
    hideTools: 'Hide',
    showReasoning: 'Show',
    hideReasoning: 'Hide',
    showThinking: 'Show',
    hideThinking: 'Hide',
    thoughtsCollapsedHint: 'Thinking',
    toolInput: 'Input: ',
    toolResult: 'Result: ',
    toolFallback: 'Completed {tool}.',
    toolStatus: {
      pending: 'Running',
      success: 'Done',
      error: 'Failed'
    },
    agentNames: {
      supervisor: 'Supervisor Agent',
      diagnosis: 'Diagnosis Agent',
      analyze: 'Analyze Agent',
      guide: 'Guide Agent'
    },
    timelineTitles: {
      supervisorInitial: 'Analyzing the request',
      diagnosisInitial: 'Diagnosing the Kubernetes issue',
      analyzeInitial: 'Fast scanning cluster status',
      guideInitial: 'Preparing explanation and guidance',
      afterTool: 'Reviewing tool results',
      afterSubAgent: 'Merging sub-agent output',
      continueAnalysis: 'Continuing the analysis',
      error: 'Handling an error'
    },
    timelineFallbacks: {
      unknownAgent: 'Agent',
      emptyReasoning: 'Continuing to process the current request.',
      errorDescription: 'An unexpected error occurred while processing the request.',
      syntheticThinking: 'Thinking and selecting appropriate tool...',
      syntheticThinkingCompleted: 'Identified next diagnostic step and preparing tool execution',
      syntheticAnalysis: 'Analyzing the tool execution results...',
      syntheticAnalysisCompleted: 'Completed analysis of tool execution results and proceeding to next step',
      emptyAnswer: 'Diagnosis did not complete. The request might have timed out and been cancelled due to excessive resources in the namespace. Please specify a more targeted namespace, or inspect the thinking timeline above for details.'
    },
    toolNames: {
      callAnalyzeAgent: 'Call analyze_agent',
      callDiagnosisAgent: 'Call diagnosis_agent',
      callGuideAgent: 'Call guide_agent'
    }
  },
  settings: {
    title: 'System Settings',
    apiConfig: {
      title: 'API Configuration',
      baseUrl: 'Backend Service URL',
      baseUrlPlaceholder: 'Please enter backend service URL, e.g.: http://localhost:10008',
      testConnection: 'Test Connection',
      connectionSuccess: 'Connection successful',
      connectionFailed: 'Connection failed'
    },
    userConfig: {
      title: 'User Configuration',
      userId: 'User ID',
      userIdPlaceholder: 'Please enter platform user ID',
      chatId: 'Chat ID',
      chatIdPlaceholder: 'Please enter chat ID (optional, leave empty for auto-generation)',
      paasManagedAlert: 'The User ID is managed by the PaaS platform login session and cannot be manually modified.',
      paasSessionActive: 'PaaS Login Session Active'
    },
    validation: {
      baseUrlRequired: 'Please enter the backend service URL',
      baseUrlInvalid: 'Please enter a valid URL',
      userIdRequired: 'Please enter user ID',
      baseUrlMissing: 'Please enter the backend service URL first'
    },
    saveConfig: 'Save Configuration',
    saveSuccess: 'Configuration saved successfully',
    saveFailed: 'Failed to save configuration',
    help: {
      title: 'Instructions',
      baseUrlHelp: 'Enter the Supervisor backend URL, for example: http://localhost:10008',
      userIdHelp: 'Used to identify the platform operator and link approvals with audit records',
      chatIdHelp: 'Used to identify the current chat session, leave empty for auto-generation',
      apiHelp: 'The chat page calls {url}/api/assistant/chat/structured'
    }
  },
  auth: {
    loginRequired: 'PaaS Authentication Required',
    loginRequiredDesc: 'The current session is not logged in or has expired. To ensure data security and audit compliance, please log in to the PaaS platform first, and access the Diagnostic Assistant through the platform portal.',
    backToPaaS: 'Back to PaaS Platform'
  }
}
