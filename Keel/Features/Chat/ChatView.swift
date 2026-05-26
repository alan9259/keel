import SwiftUI
import SwiftData

/// The AI companion — a warm, streaming chat backed by `ChatService`.
struct ChatView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @Query(sort: \ChatMessage.createdAt) private var messages: [ChatMessage]
    @State private var input = ""
    @State private var isStreaming = false
    @State private var streamingID: UUID?
    @State private var showSupport = false

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Companion", titleSize: 28,
                         subtitle: "A supportive space, not medical advice") { dismiss() }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 4)

            Button { showSupport = true } label: {
                Label("Get support", systemImage: "lifepreserver")
                    .font(KeelFont.sans(13, weight: .semibold)).foregroundStyle(theme.accent)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.bottom, 8)
            .accessibilityHint("Crisis and support lines for your region")

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(messages) { message in
                            bubble(message).id(message.id)
                        }
                        Color.clear.frame(height: 1).id("bottom")
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                }
                .onChange(of: messages.count) { _, _ in scrollToBottom(proxy) }
                .onChange(of: isStreaming) { _, _ in scrollToBottom(proxy) }
                .onAppear { scrollToBottom(proxy, animated: false) }
            }

            if !env.proposals.pending.isEmpty {
                proposalCards
            }

            inputBar
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
        .sheet(isPresented: $showSupport) { SupportView() }
        .onDisappear { env.speech.reset() }
        .onAppear {
            seedGreetingIfNeeded()
            #if DEBUG
            if let demo = DebugHarness.chatDemoMessage, messages.count <= 1, input.isEmpty {
                input = demo
                send()
            }
            #endif
        }
    }

    // MARK: Bubbles

    @ViewBuilder
    private func bubble(_ message: ChatMessage) -> some View {
        let isUser = message.role == .user
        HStack {
            if isUser { Spacer(minLength: 40) }
            Group {
                if message.text.isEmpty && message.id == streamingID {
                    TypingDots()
                } else {
                    Text(message.text)
                        .font(KeelFont.bodyLarge)
                        .foregroundStyle(isUser ? theme.background : theme.text)
                        .lineSpacing(3)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(isUser ? theme.accent : theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(isUser ? Color.clear : theme.border, lineWidth: 1)
            )
            if !isUser { Spacer(minLength: 40) }
        }
    }

    // MARK: Proposals
    //
    // A write the companion has drafted. Nothing is saved until she taps confirm,
    // so she stays in control of her own record.

    private var proposalCards: some View {
        VStack(spacing: 10) {
            ForEach(env.proposals.pending) { proposal in
                proposalCard(proposal)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
    }

    private func proposalCard(_ proposal: AgentProposal) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "square.and.pencil").font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(theme.sage)
                Text(proposal.title).keelEyebrow()
            }
            Text(proposal.summary)
                .font(KeelFont.body).foregroundStyle(theme.text)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 10) {
                Button { env.proposals.dismiss(proposal) } label: {
                    Text("Not now")
                        .font(KeelFont.sans(15, weight: .medium)).foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.inputBackground)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Dismiss, \(proposal.summary)")

                Button { confirm(proposal) } label: {
                    Text(proposal.confirmLabel)
                        .font(KeelFont.sans(15, weight: .semibold)).foregroundStyle(theme.background)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(theme.accent)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(proposal.confirmLabel), \(proposal.summary)")
            }
        }
        .padding(16)
        .background(theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).stroke(theme.border, lineWidth: 1))
    }

    private func confirm(_ proposal: AgentProposal) {
        env.proposals.confirm(proposal)
        Haptics.success()
        env.requestSync()
    }

    // MARK: Input

    private var inputBar: some View {
        HStack(spacing: 10) {
            TextField("Share what's on your mind\u{2026}", text: $input, axis: .vertical)
                .font(KeelFont.bodyLarge)
                .foregroundStyle(theme.text)
                .lineLimit(1...5)
                .submitLabel(.send)
                // The field grows for long messages, but Return sends rather than
                // adding a line: a newline arriving means she pressed enter.
                .onChange(of: input) { _, newValue in
                    guard newValue.contains("\n") else { return }
                    input = newValue.replacingOccurrences(of: "\n", with: "")
                    send()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(theme.inputBackground)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

            // Speak your message: dictation fills the field as you talk.
            VoiceInputButton(isRecording: env.speech.isRecording) { toggleVoice() }

            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(theme.background)
                    .frame(width: 40, height: 40)
                    .background(canSend ? theme.accent : theme.muted.opacity(0.4))
                    .clipShape(Circle())
            }
            .disabled(!canSend)
            .accessibilityLabel("Send message")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(theme.background)
        .overlay(Divider().background(theme.border), alignment: .top)
        // Stream the live transcript into the field while dictating.
        .onChange(of: env.speech.transcript) { _, t in if env.speech.isRecording { input = t } }
    }

    private func toggleVoice() {
        if env.speech.isRecording {
            env.speech.stop()
        } else {
            Task {
                guard await env.speech.requestAuthorization() else { return }
                try? env.speech.start(seed: input)
            }
        }
    }

    private var canSend: Bool {
        !isStreaming && !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: Actions

    private func seedGreetingIfNeeded() {
        guard messages.isEmpty else { return }
        env.context.insert(ChatMessage(role: .assistant, text: KeelChatPrompt.greeting, ownerID: env.auth.ownerID))
        try? env.context.save()
    }

    private func send() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isStreaming else { return }
        input = ""
        env.speech.reset() // stop any dictation so it can't refill the field
        Haptics.light()

        env.context.insert(ChatMessage(role: .user, text: text, ownerID: env.auth.ownerID))
        let assistant = ChatMessage(role: .assistant, text: "", ownerID: env.auth.ownerID)
        env.context.insert(assistant)
        try? env.context.save()

        streamingID = assistant.id
        isStreaming = true
        let history = conversationHistory()

        Task {
            do {
                for try await delta in env.chat.streamReply(history: history, system: KeelChatPrompt.system) {
                    assistant.text += delta
                }
            } catch {
                if assistant.text.isEmpty {
                    assistant.text = "Sorry, I couldn't reach the companion just now. Please try again in a moment."
                }
            }
            try? env.context.save()
            isStreaming = false
            streamingID = nil
        }
    }

    /// Non-empty messages in order, with any leading assistant turns dropped so
    /// the transcript starts with a user message (Messages API requirement).
    private func conversationHistory() -> [ChatTurn] {
        let descriptor = FetchDescriptor<ChatMessage>(sortBy: [SortDescriptor(\.createdAt)])
        let all = (try? env.context.fetch(descriptor)) ?? []
        let nonEmpty = all.filter { !$0.text.isEmpty }
        let trimmed = nonEmpty.drop(while: { $0.role == .assistant })
        return trimmed.map { ChatTurn(role: $0.role, text: $0.text) }
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy, animated: Bool = true) {
        if animated {
            withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("bottom", anchor: .bottom) }
        } else {
            proxy.scrollTo("bottom", anchor: .bottom)
        }
    }
}

/// Animated three-dot typing indicator.
private struct TypingDots: View {
    @Environment(\.keelTheme) private var theme
    @State private var phase = 0

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(theme.muted)
                    .frame(width: 7, height: 7)
                    .opacity(phase == i ? 1 : 0.3)
            }
        }
        .onAppear {
            Timer.scheduledTimer(withTimeInterval: 0.35, repeats: true) { _ in
                withAnimation(.easeInOut(duration: 0.2)) { phase = (phase + 1) % 3 }
            }
        }
    }
}
