import SwiftUI
import PhotosUI

// 후원자 이름(또는 로고): 고급 스페셜티 커피 이상이 보내면 개발자가 확인한 뒤 도움말·후원 화면의 '후원해 주신 분들'에 올라간다.

/// 후원 화면 안: 이름·로고 보내기 + 지금 상태 (검토 중 / 올라감)
struct SponsorForm: View {
    @State private var name = ""
    @State private var pick: PhotosPickerItem? = nil
    @State private var logo: UIImage? = nil
    @State private var mine: StoreAPI.Mine? = nil
    @State private var sending = false
    @State private var note: String? = nil

    var body: some View {
        Card(stroke: Theme.gold) {
            Text("후원자 이름 올리기").appFont(17, .bold)
            Text("도움말의 '후원해 주신 분들'에 이름(또는 로고)이 올라가요. 개발자가 확인한 뒤 보여요.")
                .appFont(13).foregroundStyle(Theme.sub).fixedSize(horizontal: false, vertical: true)
            if let a = mine?.approved { Text("올라간 이름 · \(a.name)").appFont(13, .bold).foregroundStyle(Theme.teal) }
            if let p = mine?.pending { Text("확인 중 · \(p.name)").appFont(13, .bold).foregroundStyle(Theme.sub) }
            TextField("이름 또는 닉네임 (40자까지)", text: $name)
                .appFont(15).textInputAutocapitalization(.never)
                .padding(.horizontal, 12).frame(minHeight: 44)
                .background(Theme.bg, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border))
                .onChange(of: name) { _, v in if v.count > 40 { name = String(v.prefix(40)) } }
            HStack(spacing: 10) {
                if let logo { Image(uiImage: logo).resizable().scaledToFit().frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 8)) }
                PhotosPicker(selection: $pick, matching: .images) {
                    Text(logo == nil ? "로고 고르기 (선택)" : "로고 바꾸기").appFont(14, .bold).foregroundStyle(Theme.teal)
                }
                if logo != nil { Button("빼기") { logo = nil; pick = nil }.appFont(14).foregroundStyle(Theme.muted) }
                Spacer(minLength: 0)
            }
            Button { Task { await send() } } label: {
                Text(sending ? "보내는 중…" : "보내기").appFont(15, .bold).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 44).background(Theme.teal, in: RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain).disabled(sending || name.trimmingCharacters(in: .whitespaces).isEmpty)
            if let note { Text(note).appFont(13, .bold).foregroundStyle(Theme.teal).fixedSize(horizontal: false, vertical: true) }
        }
        .task { mine = try? await StoreAPI.mine() }
        .onChange(of: pick) { _, p in
            Task { if let d = try? await p?.loadTransferable(type: Data.self), let img = UIImage(data: d) { logo = img } }
        }
    }

    private func send() async {
        sending = true; defer { sending = false }
        do {
            try await StoreAPI.sponsor(name: name.trimmingCharacters(in: .whitespaces), logo: logo.flatMap(Self.dataURL))
            note = "보냈어요. 개발자가 확인하면 올라가요."
            mine = try? await StoreAPI.mine()
        } catch {
            note = error.localizedDescription
        }
    }

    /// 서버 한도(200KB)에 맞게: 긴 변 256px 로 줄여 PNG, 크면 JPEG
    static func dataURL(_ img: UIImage) -> String? {
        let side: CGFloat = 256, scale = min(1, side / max(img.size.width, img.size.height))
        let size = CGSize(width: img.size.width * scale, height: img.size.height * scale)
        let fmt = UIGraphicsImageRendererFormat.default(); fmt.scale = 1
        let small = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        if let png = small.pngData(), png.count <= 190_000 { return "data:image/png;base64," + png.base64EncodedString() }
        for q in [0.85, 0.7, 0.5] {
            if let jpg = small.jpegData(compressionQuality: q), jpg.count <= 190_000 { return "data:image/jpeg;base64," + jpg.base64EncodedString() }
        }
        return nil
    }
}

/// 후원해 주신 분들 (도움말·후원 화면). 아직 없으면 아무것도 안 보인다
struct SponsorList: View {
    @State private var list: [StoreAPI.Sponsor] = []

    var body: some View {
        Group {
            if !list.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("후원해 주신 분들").appFont(15, .bold)
                    FlowNames(list: list)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            }
        }
        .task { list = await StoreAPI.sponsors() }
    }
}

private struct FlowNames: View {
    let list: [StoreAPI.Sponsor]
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8, alignment: .leading)], alignment: .leading, spacing: 8) {
            ForEach(list, id: \.self) { s in
                HStack(spacing: 6) {
                    if let l = s.logo, let u = URL(string: l) {
                        AsyncImage(url: u) { $0.resizable().scaledToFit() } placeholder: { Theme.track }
                            .frame(width: 24, height: 24).clipShape(RoundedRectangle(cornerRadius: 5))
                    }
                    Text(s.name).appFont(13, .semibold).lineLimit(1)
                }
            }
        }
    }
}
