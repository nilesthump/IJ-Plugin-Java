package edu.courseassist.ui;

import edu.courseassist.contract.ApplicationController;
import edu.courseassist.contract.CredentialUpdate;
import edu.courseassist.contract.CredentialUpdateSink;
import edu.courseassist.contract.ApplicationContracts.ApplicationEvent;
import edu.courseassist.contract.ApplicationContracts.ApplicationEventMeta;
import edu.courseassist.contract.ApplicationContracts.ApplicationViewState;
import edu.courseassist.contract.ApplicationContracts.CorpusBase;
import edu.courseassist.contract.ApplicationContracts.DisablePlugin;
import edu.courseassist.contract.ApplicationContracts.EnablePlugin;
import edu.courseassist.contract.ApplicationContracts.ImportCoursePdfs;
import edu.courseassist.contract.ApplicationContracts.MaterialTarget;
import edu.courseassist.contract.ApplicationContracts.MaterialView;
import edu.courseassist.contract.ApplicationContracts.RemoveCourseMaterial;
import edu.courseassist.contract.ApplicationContracts.ReplaceCourseMaterial;
import edu.courseassist.contract.ApplicationContracts.ProviderConfiguration;
import edu.courseassist.contract.ApplicationContracts.ProviderProfile;
import edu.courseassist.contract.ApplicationContracts.ProfileView;
import edu.courseassist.contract.ApplicationContracts.UpdateProviderConfig;
import edu.courseassist.contract.ApplicationContracts.WorkState;
import edu.courseassist.contract.CommonContracts.CommandResult;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/** Application-wide library and work-state controls; no session is inferred or attached. */
final class ApplicationWorkspace {
    private final ApplicationController controller;
    private final CredentialUpdateSink credentials;
    private final Consumer<String> feedback;
    private final BooleanSupplier active;
    private final JPanel root = new JPanel(new BorderLayout(4, 4));
    private final JLabel credentialsState = new JLabel("凭据状态待恢复");
    private final JLabel workState = new JLabel("等待应用状态");
    private final DefaultListModel<MaterialView> materialModel = new DefaultListModel<>();
    private final JList<MaterialView> materials = new JList<>(materialModel);
    private final JButton workSwitch = new JButton("启用");
    private final JButton importPdf = new JButton("导入 PDF");
    private final JButton replace = new JButton("更换所选");
    private final JButton remove = new JButton("移除所选");
    private final JButton configure = new JButton("服务配置");
    private ApplicationViewState state;

    ApplicationWorkspace(ApplicationController controller, CredentialUpdateSink credentials,
                         Consumer<String> feedback, BooleanSupplier active) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.credentials = credentials;
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.active = Objects.requireNonNull(active, "active");
        materials.setName("materialList");
        materials.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        materials.setCellRenderer((list, value, index, selected, focused) -> {
            var label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(
                    list, value, index, selected, focused);
            if (value != null) {
                label.setText("资料：" + value.name() + " · " + value.status()
                        + value.sourceVersionId().map(version -> " · 版本 " + version).orElse("")
                        + " · " + value.changedAt()
                        + value.physicalPageCount().map(count -> " · " + count + " 页").orElse(""));
            }
            return label;
        });
        materials.addListSelectionListener(event -> updateActions());
        workSwitch.setName("workSwitch");
        importPdf.setName("importPdf");
        replace.setName("replaceMaterial");
        remove.setName("removeMaterial");
        configure.setName("configureProviders");
        workSwitch.addActionListener(event -> toggleWork());
        importPdf.addActionListener(event -> chooseImport());
        replace.addActionListener(event -> chooseReplacement());
        remove.addActionListener(event -> removeSelected());
        configure.addActionListener(event -> configureProviders());

        JPanel actions = new JPanel(new GridLayout(0, 2, 4, 4));
        actions.add(workSwitch);
        actions.add(importPdf);
        actions.add(replace);
        actions.add(remove);
        actions.add(configure);
        JPanel header = new JPanel(new GridLayout(0, 1));
        header.add(workState); header.add(credentialsState);
        root.add(header, BorderLayout.NORTH);
        root.add(new JScrollPane(materials), BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        root.setName("applicationWorkspace");
        updateActions();
    }

    JPanel component() { return root; }

    void apply(ApplicationViewState next) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("UI state must apply on EDT");
        if (state != null && next.revision() < state.revision()) return;
        String selectedId = materials.getSelectedValue() == null ? null : materials.getSelectedValue().documentId();
        state = next;
        workState.setText("工作状态：" + next.workState() + " · 课程库：" + next.corpusState()
                + (next.hasUnsavedState() ? " · 有未保存状态" : ""));
        credentialsState.setText("生成凭据：" + credentialLabel(next.generationProfile()) + " · 嵌入凭据：" + credentialLabel(next.embeddingProfile()) + "（已保存不代表远端认证通过）");
        materialModel.clear();
        next.materials().forEach(materialModel::addElement);
        if (selectedId != null) {
            for (int i = 0; i < materialModel.size(); i++) {
                if (selectedId.equals(materialModel.get(i).documentId())) {
                    materials.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateActions();
    }

    private static String credentialLabel(Optional<ProfileView> profile) {
        return profile.filter(p -> p.credentialStatus() == edu.courseassist.contract.ApplicationContracts.CredentialStatus.CONFIGURED).isPresent()
                ? "已保存" : "未保存";
    }

    private void updateActions() {
        boolean ready = state != null;
        workSwitch.setEnabled(ready && (state.workState() == WorkState.OFF || state.workState() == WorkState.ON));
        workSwitch.setText(ready && state.workState() == WorkState.ON ? "停用" : "启用");
        importPdf.setEnabled(ready && state.workState() == WorkState.ON);
        boolean chosen = ready && state.workState() == WorkState.ON && materials.getSelectedValue() != null
                && materials.getSelectedValue().status() != edu.courseassist.contract.ApplicationContracts.MaterialStatus.REMOVED;
        replace.setEnabled(chosen);
        remove.setEnabled(chosen);
        configure.setEnabled(ready && state.workState() == WorkState.OFF);
    }

    private void toggleWork() {
        if (state == null) return;
        ApplicationEventMeta meta = meta(state.revision());
        if (state.workState() == WorkState.OFF) dispatch(new EnablePlugin(meta));
        else if (state.workState() == WorkState.ON) dispatch(new DisablePlugin(meta));
    }

    private void chooseImport() {
        if (state == null) return;
        long revision = state.revision();
        CorpusBase base = state.corpusBase();
        var files = choosePdfs(true);
        if (!files.isEmpty()) dispatch(new ImportCoursePdfs(meta(revision), files, base));
    }

    private void chooseReplacement() {
        if (state == null || materials.getSelectedValue() == null) return;
        MaterialTarget target = target(materials.getSelectedValue());
        long revision = state.revision();
        CorpusBase base = state.corpusBase();
        var files = choosePdfs(false);
        if (!files.isEmpty()) dispatch(new ReplaceCourseMaterial(meta(revision), target, files.getFirst(), base));
    }

    private void removeSelected() {
        if (state == null || materials.getSelectedValue() == null) return;
        MaterialTarget target = target(materials.getSelectedValue());
        dispatch(new RemoveCourseMaterial(meta(state.revision()), target, state.corpusBase()));
    }

    private void configureProviders() {
        if (state == null || state.workState() != WorkState.OFF) return;
        long appRevision = state.revision();
        long configRevision = state.configRevision();
        ProviderFields generation = new ProviderFields("生成服务", state.generationProfile());
        ProviderFields embedding = new ProviderFields("嵌入服务", state.embeddingProfile());
        JPanel form = new JPanel(new GridLayout(1, 2, 8, 8));
        form.add(generation.panel);
        form.add(embedding.panel);
        embedding.panel.add(new JLabel("<html>必须是 embedding 模型，不能填写聊天模型。<br>维度来自该嵌入模型文档，不能任意填写。<br>生成与嵌入可使用不同服务和 key。</html>"));
        int choice = JOptionPane.showConfirmDialog(root, form, "服务配置（仅停用时可修改）",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            generation.clearPassword();
            embedding.clearPassword();
            return;
        }

        ProviderConfiguration configuration;
        try {
            configuration = new ProviderConfiguration(generation.profile(), embedding.profile());
        } catch (RuntimeException invalid) {
            generation.clearPassword();
            embedding.clearPassword();
            feedback.accept("服务配置无效：请检查地址、模型和维度");
            return;
        }
        char[] generationKey = generation.key.getPassword();
        char[] embeddingKey = embedding.key.getPassword();
        ArrayList<CredentialUpdate.Change> changes = new ArrayList<>();
        try {
            if (generation.removeKey.isSelected()) {
                changes.add(new CredentialUpdate.Change(CredentialUpdate.Slot.GENERATION,
                        CredentialUpdate.Action.REMOVE, new char[0]));
            } else if (generationKey.length > 0) {
                changes.add(new CredentialUpdate.Change(CredentialUpdate.Slot.GENERATION,
                        CredentialUpdate.Action.REPLACE, generationKey));
            }
            if (embedding.removeKey.isSelected()) {
                changes.add(new CredentialUpdate.Change(CredentialUpdate.Slot.EMBEDDING,
                        CredentialUpdate.Action.REMOVE, new char[0]));
            } else if (embeddingKey.length > 0) {
                changes.add(new CredentialUpdate.Change(CredentialUpdate.Slot.EMBEDDING,
                        CredentialUpdate.Action.REPLACE, embeddingKey));
            }
        } finally {
            Arrays.fill(generationKey, '\0');
            Arrays.fill(embeddingKey, '\0');
            generation.clearPassword();
            embedding.clearPassword();
        }
        CredentialUpdate update = new CredentialUpdate(changes);
        if (changes.isEmpty()) {
            if (configurationStillCurrent(appRevision, configRevision)) {
                dispatch(new UpdateProviderConfig(meta(appRevision), configRevision,
                        configuration, Optional.empty()));
            } else feedback.accept("应用状态已变化，请重新打开服务配置");
            update.close();
            return;
        }
        if (credentials == null) {
            update.close();
            feedback.accept("凭据入口尚未接入");
            return;
        }
        try {
            credentials.stage(update).whenComplete((ref, failure) -> {
                update.close();
                SwingUtilities.invokeLater(() -> {
                    if (!active.getAsBoolean()) return;
                    if (failure != null) feedback.accept("凭据暂存失败：" + failure.getClass().getSimpleName());
                    else if (!configurationStillCurrent(appRevision, configRevision)) {
                        feedback.accept("应用状态已变化，请重新打开服务配置");
                    } else dispatch(new UpdateProviderConfig(meta(appRevision), configRevision,
                            configuration, Optional.of(ref)));
                });
            });
        } catch (RuntimeException failure) {
            update.close();
            feedback.accept("凭据暂存失败：" + failure.getClass().getSimpleName());
        }
    }

    private boolean configurationStillCurrent(long appRevision, long configRevision) {
        return active.getAsBoolean() && state != null && state.workState() == WorkState.OFF
                && state.revision() == appRevision && state.configRevision() == configRevision;
    }

    private static MaterialTarget target(MaterialView material) {
        return new MaterialTarget(material.documentId(), material.sourceVersionId(), material.materialRevision());
    }

    private static ApplicationEventMeta meta(long revision) {
        return new ApplicationEventMeta(UUID.randomUUID().toString(), revision);
    }

    private java.util.List<Path> choosePdfs(boolean multiple) {
        // AWT delegates to the Windows native file picker (Explorer navigation/search).
        java.awt.Window owner = SwingUtilities.getWindowAncestor(root);
        java.awt.FileDialog chooser = owner instanceof java.awt.Dialog dialog
                ? new java.awt.FileDialog(dialog, "选择 PDF", java.awt.FileDialog.LOAD)
                : new java.awt.FileDialog(owner instanceof java.awt.Frame frame ? frame : null, "选择 PDF", java.awt.FileDialog.LOAD);
        try {
            chooser.setMultipleMode(multiple); chooser.setFile("*.pdf"); chooser.setVisible(true);
            var selected = Arrays.stream(chooser.getFiles()).map(file -> file.toPath().toAbsolutePath()).toList();
            if (selected.stream().anyMatch(file -> !file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))) {
                feedback.accept("请选择 PDF 文件"); return java.util.List.of();
            }
            return selected;
        } finally { chooser.dispose(); }
    }

    private void dispatch(ApplicationEvent event) {
        if (!active.getAsBoolean()) return;
        controller.dispatch(event).whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (!active.getAsBoolean()) return;
            if (failure != null) feedback.accept("资料或开停操作失败：" + failure.getClass().getSimpleName());
            else if (result.outcome() == edu.courseassist.contract.CommonContracts.Outcome.REJECTED) {
                feedback.accept("操作未应用：" + result.rejection().orElseThrow());
            } else feedback.accept("操作已接收");
        }));
    }

    private static final class ProviderFields {
        final JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));
        final JTextField id = new JTextField();
        final JTextField provider = new JTextField();
        final JTextField endpoint = new JTextField();
        final JTextField model = new JTextField();
        final JTextField dimensions = new JTextField();
        final JPasswordField key = new JPasswordField();
        final JCheckBox removeKey = new JCheckBox("删除已保存的 key（删除后无法启用）");

        ProviderFields(String title, Optional<ProfileView> current) {
            panel.add(new JLabel(title));
            current.ifPresent(view -> {
                ProviderProfile profile = view.profile();
                id.setText(profile.profileId());
                provider.setText(profile.provider());
                endpoint.setText(profile.endpoint().toString());
                model.setText(profile.modelId());
                profile.embeddingDimensions().ifPresent(value -> dimensions.setText(Integer.toString(value)));
            });
            add("配置 ID", id);
            add("提供方", provider);
            add("Base URL（不含 /chat/completions 或 /embeddings）", endpoint);
            add("模型 ID", model);
            add("嵌入维度（生成服务可空）", dimensions);
            add("新 API key（留空则保持）", key);
            panel.add(removeKey);
            removeKey.addItemListener(event -> key.setEnabled(!removeKey.isSelected()));
            key.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void update() {
                    boolean empty = key.getDocument().getLength() == 0;
                    if (!empty) removeKey.setSelected(false);
                    removeKey.setEnabled(empty);
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            });
            panel.add(new JLabel("<html>更换 key：直接填写新值，无需先删除。<br>留空且不勾选删除：保留现有凭据。</html>"));
        }

        private void add(String label, javax.swing.JComponent field) {
            panel.add(new JLabel(label));
            panel.add(field);
        }

        ProviderProfile profile() {
            String dimensionText = dimensions.getText().strip();
            return new ProviderProfile(id.getText().strip(), provider.getText().strip(),
                    URI.create(endpoint.getText().strip()), model.getText().strip(),
                    dimensionText.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(dimensionText)));
        }

        void clearPassword() { key.setText(""); }
    }
}
