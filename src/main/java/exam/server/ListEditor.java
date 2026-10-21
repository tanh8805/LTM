package exam.server;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** Một danh sách chuỗi có ô nhập kèm nút Thêm và nút Xoá. */
class ListEditor extends JPanel {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final JTextField input = new JTextField(24);
    private final boolean lowerCase;

    ListEditor(List<String> items, boolean lowerCase) {
        super(new BorderLayout(0, 8));
        this.lowerCase = lowerCase;
        items.forEach(model::addElement);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JButton add = new JButton("Thêm"), remove = new JButton("Xoá");
        add.addActionListener(e -> addInput());
        input.addActionListener(e -> addInput());
        remove.addActionListener(e -> list.getSelectedValuesList().forEach(model::removeElement));
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottom.add(input);
        bottom.add(add);
        bottom.add(remove);
        add(new JScrollPane(list), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Thêm giá trị đang gõ nếu không rỗng và chưa có (so khớp không phân biệt hoa thường). */
    private void addInput() {
        String text = input.getText().trim();
        if (lowerCase) { text = text.toLowerCase(); }
        input.setText("");
        if (text.isEmpty()) { return; }
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).equalsIgnoreCase(text)) { return; }
        }
        model.addElement(text);
        list.ensureIndexIsVisible(model.size() - 1);
    }

    List<String> items() {
        addInput();                 // giá trị gõ dở chưa bấm Thêm vẫn được tính
        return new ArrayList<>(java.util.Collections.list(model.elements()));
    }
}
