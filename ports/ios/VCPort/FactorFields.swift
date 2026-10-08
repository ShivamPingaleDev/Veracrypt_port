import SwiftUI

extension ContentView {
    /// One secure password and one PIM field. Open, Create, Tools, and the
    /// nested password all use this. The value stays in the caller's state.
    @ViewBuilder
    func passwordAndPim(
        password: Binding<String>,
        passwordPrompt: String,
        passwordTag: String,
        pim: Binding<String>,
        pimPrompt: String,
        pimTag: String
    ) -> some View {
        passwordAndPim(
            password: password,
            passwordPrompt: passwordPrompt,
            passwordTag: passwordTag,
            pim: pim,
            pimPrompt: pimPrompt,
            pimTag: pimTag,
            between: { EmptyView() }
        )
    }

    @ViewBuilder
    func passwordAndPim<Between: View>(
        password: Binding<String>,
        passwordPrompt: String,
        passwordTag: String,
        pim: Binding<String>,
        pimPrompt: String,
        pimTag: String,
        @ViewBuilder between: () -> Between
    ) -> some View {
        SecureField(passwordPrompt, text: password)
            .neverSaveHistory()
            .portTag(passwordTag)
        between()
        TextField(pimPrompt, text: pim)
            .keyboardType(.numberPad)
            .portTag(pimTag)
    }
}
