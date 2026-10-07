// Owner: Nguoi2

package exam.client.student;

import exam.common.model.QuestionView;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Cửa sổ của sinh viên: màn hình đăng nhập rồi màn hình làm bài.
 *
 * Màn hình đăng nhập : Student ID, Password, Session Code, nút Login, dòng trạng thái.
 * Màn hình làm bài   : câu hỏi + 4 đáp án A-D, Previous/Next, Mark, danh sách câu, đồng hồ đếm ngược, Submit.
 *
 * Quy tắc: giao diện KHÔNG tự quyết định hết giờ. Đồng hồ chỉ hiển thị thời gian còn lại theo đồng hồ Server;
 * khi hết giờ Server gửi EXAM_END và lúc đó bài mới bị khóa.
 * Không dùng hộp thoại khi đang thi (hộp thoại làm cửa sổ thi mất focus và bị tính là vi phạm luật 5).
 */
public class StudentFrame extends JFrame implements StudentView {

    private static final String CARD_LOGIN = "login";
    private static final String CARD_EXAM = "exam";
    private static final String[] OPTION_LETTERS = {"A", "B", "C", "D"};

    private final StudentActions actions;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    // --- đăng nhập ---
    private final JTextField studentCodeField = new JTextField(14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JTextField examCodeField = new JTextField(14);
    private final JButton loginButton = new JButton("Login");
    private final JLabel loginMessageLabel = new JLabel(" ");

    // --- thanh trạng thái (hiện ở cả hai màn hình) ---
    private final JLabel statusLabel = new JLabel("Status: Connecting...");
    private final JLabel loginLabel = new JLabel("Login: -");
    private final JLabel heartbeatLabel = new JLabel("Heartbeat: -");

    // --- làm bài ---
    private final JLabel titleLabel = new JLabel(" ");
    private final JLabel timerLabel = new JLabel("Còn lại: --:--");
    private final JTextArea questionArea = new JTextArea(5, 40);
    private final JRadioButton[] optionButtons = new JRadioButton[4];
    private final ButtonGroup optionGroup = new ButtonGroup();
    private final JButton previousButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private final JButton markButton = new JButton("Mark");
    private final JButton submitButton = new JButton("Submit");
    private final DefaultListModel<String> questionListModel = new DefaultListModel<>();
    private final JList<String> questionList = new JList<>(questionListModel);
    private final JLabel noticeLabel = new JLabel(" ");
    private final JLabel roomLabel = new JLabel(" ");
    private final JTextArea logArea = new JTextArea(4, 40);

    private List<QuestionView> questions = new ArrayList<>();
    private final Map<Integer, Integer> chosenAnswers = new HashMap<>();
    private final Set<Integer> markedQuestionIds = new HashSet<>();
    private int currentIndex = 0;
    private boolean examLocked = true;
    private boolean updatingFromCode = false;
    private boolean waitingSubmitConfirmation = false;
    private Timer countdownTimer;
    private Timer submitConfirmTimer;

    public StudentFrame(StudentActions actions, String defaultStudentCode, String defaultPassword, String defaultExamCode) {
        super("Student - Thi trắc nghiệm");
        this.actions = actions;

        studentCodeField.setText(defaultStudentCode);
        passwordField.setText(defaultPassword);
        examCodeField.setText(defaultExamCode);

        cardPanel.add(buildLoginPanel(), CARD_LOGIN);
        cardPanel.add(buildExamPanel(), CARD_EXAM);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        statusPanel.add(statusLabel);
        statusPanel.add(loginLabel);
        statusPanel.add(heartbeatLabel);

        setLayout(new BorderLayout());
        add(statusPanel, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);

        // Luật 5 (Focus): mỗi lần cửa sổ thi mất focus, báo cho RuleEngine đếm.
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
            }

            @Override
            public void windowLostFocus(WindowEvent event) {
                actions.onFocusLost();
            }
        });

        setSize(820, 560);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    // ------------------------------------------------------------------
    // Dựng giao diện
    // ------------------------------------------------------------------

    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Student ID:"), c);
        c.gridx = 1;
        panel.add(studentCodeField, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Password:"), c);
        c.gridx = 1;
        panel.add(passwordField, c);

        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Session Code:"), c);
        c.gridx = 1;
        panel.add(examCodeField, c);

        c.gridx = 1;
        c.gridy = 3;
        panel.add(loginButton, c);
        c.gridy = 4;
        panel.add(loginMessageLabel, c);

        loginButton.addActionListener(event -> {
            loginButton.setEnabled(false);
            loginMessageLabel.setText("Đang đăng nhập...");
            actions.login(studentCodeField.getText().trim(), new String(passwordField.getPassword()),
                    examCodeField.getText().trim());
        });
        return panel;
    }

    private JPanel buildExamPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.add(titleLabel, BorderLayout.WEST);
        top.add(timerLabel, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        questionList.setPreferredSize(new Dimension(150, 100));
        questionList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingFromCode && questionList.getSelectedIndex() >= 0) {
                currentIndex = questionList.getSelectedIndex();
                renderCurrentQuestion();
            }
        });
        panel.add(new JScrollPane(questionList), BorderLayout.WEST);

        questionArea.setEditable(false);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JScrollPane(questionArea), BorderLayout.NORTH);
        JPanel options = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 4, 4, 4);
        for (int i = 0; i < 4; i++) {
            final int choice = i;
            optionButtons[i] = new JRadioButton();
            optionGroup.add(optionButtons[i]);
            optionButtons[i].addActionListener(event -> onOptionChosen(choice));
            c.gridy = i;
            options.add(optionButtons[i], c);
        }
        center.add(options, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(previousButton);
        buttons.add(nextButton);
        buttons.add(markButton);
        buttons.add(submitButton);
        previousButton.addActionListener(event -> moveTo(currentIndex - 1));
        nextButton.addActionListener(event -> moveTo(currentIndex + 1));
        markButton.addActionListener(event -> toggleMark());
        submitButton.addActionListener(event -> onSubmitClicked());

        JPanel messages = new JPanel(new BorderLayout());
        messages.add(noticeLabel, BorderLayout.NORTH);
        messages.add(roomLabel, BorderLayout.CENTER);
        logArea.setEditable(false);
        messages.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(messages, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ------------------------------------------------------------------
    // Hành động của sinh viên (chạy trên thread giao diện)
    // ------------------------------------------------------------------

    private void onOptionChosen(int choice) {
        if (examLocked || questions.isEmpty()) {
            return;
        }
        int questionId = questions.get(currentIndex).questionId;
        if (actions.selectAnswer(questionId, choice)) {
            chosenAnswers.put(questionId, choice);
            refreshQuestionListItem(currentIndex);
        }
    }

    private void moveTo(int newIndex) {
        if (newIndex < 0 || newIndex >= questions.size()) {
            return;
        }
        currentIndex = newIndex;
        renderCurrentQuestion();
    }

    private void toggleMark() {
        if (questions.isEmpty()) {
            return;
        }
        int questionId = questions.get(currentIndex).questionId;
        if (!markedQuestionIds.remove(questionId)) {
            markedQuestionIds.add(questionId);
        }
        refreshQuestionListItem(currentIndex);
        renderCurrentQuestion();
    }

    /** Bấm Submit lần đầu: nút đổi chữ để xác nhận (không dùng hộp thoại). Bấm lần hai trong 5 giây: nộp bài. */
    private void onSubmitClicked() {
        if (examLocked) {
            return;
        }
        if (!waitingSubmitConfirmation) {
            waitingSubmitConfirmation = true;
            submitButton.setText("Bấm lần nữa để NỘP BÀI");
            submitConfirmTimer = new Timer(5000, event -> resetSubmitButton());
            submitConfirmTimer.setRepeats(false);
            submitConfirmTimer.start();
            return;
        }
        resetSubmitButton();
        if (!actions.submit()) {
            noticeLabel.setText("Chưa nộp được bài (mất kết nối?). Hãy thử lại khi đã kết nối.");
        }
    }

    private void resetSubmitButton() {
        waitingSubmitConfirmation = false;
        submitButton.setText("Submit");
        if (submitConfirmTimer != null) {
            submitConfirmTimer.stop();
        }
    }

    // ------------------------------------------------------------------
    // Hiển thị câu hỏi
    // ------------------------------------------------------------------

    private void renderCurrentQuestion() {
        if (questions.isEmpty()) {
            return;
        }
        QuestionView question = questions.get(currentIndex);
        boolean marked = markedQuestionIds.contains(question.questionId);
        questionArea.setText("Câu " + (currentIndex + 1) + "/" + questions.size()
                + (marked ? "  [ĐÃ ĐÁNH DẤU]" : "") + "\n\n" + question.content);

        optionGroup.clearSelection();
        Integer chosen = chosenAnswers.get(question.questionId);
        for (int i = 0; i < 4; i++) {
            optionButtons[i].setText(OPTION_LETTERS[i] + ". " + question.options.get(i));
            optionButtons[i].setEnabled(!examLocked);
        }
        if (chosen != null && chosen >= 0 && chosen < 4) {
            optionButtons[chosen].setSelected(true);
        }

        previousButton.setEnabled(currentIndex > 0);
        nextButton.setEnabled(currentIndex < questions.size() - 1);
        markButton.setText(marked ? "Unmark" : "Mark");

        updatingFromCode = true;
        questionList.setSelectedIndex(currentIndex);
        updatingFromCode = false;
    }

    private void rebuildQuestionList() {
        questionListModel.clear();
        for (int i = 0; i < questions.size(); i++) {
            questionListModel.addElement(describeListItem(i));
        }
    }

    private void refreshQuestionListItem(int index) {
        if (index >= 0 && index < questionListModel.size()) {
            questionListModel.set(index, describeListItem(index));
        }
    }

    /** Ví dụ: "Câu 3  [B]  *" (đã chọn B, đã đánh dấu). */
    private String describeListItem(int index) {
        int questionId = questions.get(index).questionId;
        Integer chosen = chosenAnswers.get(questionId);
        String answerText = chosen == null ? "  -  " : "  [" + OPTION_LETTERS[chosen] + "]";
        String markText = markedQuestionIds.contains(questionId) ? "  *" : "";
        return "Câu " + (index + 1) + answerText + markText;
    }

    private void lockExam(String message) {
        examLocked = true;
        for (AbstractButton button : optionButtons) {
            button.setEnabled(false);
        }
        submitButton.setEnabled(false);
        resetSubmitButton();
        noticeLabel.setText(message);
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    private void updateCountdown() {
        long remaining = actions.getRemainingSeconds();
        if (remaining < 0) {
            timerLabel.setText("Còn lại: --:--");
        } else if (remaining == 0) {
            // Không tự khóa bài: chờ Server gửi EXAM_END.
            timerLabel.setText("Hết giờ - đang chờ Server chốt bài...");
        } else {
            timerLabel.setText(String.format("Còn lại (giờ Server): %02d:%02d", remaining / 60, remaining % 60));
        }
    }

    // ------------------------------------------------------------------
    // StudentView: được gọi từ thread đọc socket nên chuyển sang thread giao diện
    // ------------------------------------------------------------------

    @Override
    public void showStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + text));
    }

    @Override
    public void showLogin(boolean ok, String message) {
        SwingUtilities.invokeLater(() -> {
            loginLabel.setText(ok ? "Login: OK" : "Login: FAIL (" + message + ")");
            loginMessageLabel.setText(ok ? "Đăng nhập thành công. Chờ giáo viên bắt đầu ca thi..." : message);
            loginButton.setEnabled(!ok);
        });
    }

    @Override
    public void showHeartbeat(int seq) {
        SwingUtilities.invokeLater(() -> heartbeatLabel.setText("Heartbeat: seq=" + seq));
    }

    @Override
    public void showExam(String title, List<QuestionView> newQuestions, Map<Integer, Integer> savedAnswers) {
        SwingUtilities.invokeLater(() -> {
            questions = new ArrayList<>(newQuestions);
            chosenAnswers.clear();
            if (savedAnswers != null) {
                chosenAnswers.putAll(savedAnswers);
            }
            currentIndex = 0;
            examLocked = false;
            submitButton.setEnabled(true);
            titleLabel.setText(title == null ? "Bài thi" : title);
            noticeLabel.setText(" ");
            rebuildQuestionList();
            renderCurrentQuestion();
            cards.show(cardPanel, CARD_EXAM);

            if (countdownTimer != null) {
                countdownTimer.stop();
            }
            countdownTimer = new Timer(500, event -> updateCountdown());
            countdownTimer.start();
            updateCountdown();
        });
    }

    @Override
    public void showRestoredAnswers(Map<Integer, Integer> answers) {
        SwingUtilities.invokeLater(() -> {
            chosenAnswers.putAll(answers);
            if (!questions.isEmpty()) {
                rebuildQuestionList();
                renderCurrentQuestion();
            }
        });
    }

    @Override
    public void showAnswerSaved(int questionId) {
        // Server đã lưu: không cần hiển thị gì thêm (danh sách câu đã hiện đáp án đã chọn).
    }

    @Override
    public void showAnswerRejected(int questionId) {
        SwingUtilities.invokeLater(() -> noticeLabel.setText("Server từ chối đáp án của một câu (đã hết giờ hoặc đã nộp)."));
    }

    @Override
    public void showSubmitted(int answeredCount) {
        SwingUtilities.invokeLater(() -> lockExam("Đã nộp bài (" + answeredCount + " câu đã trả lời). Cảm ơn bạn!"));
    }

    @Override
    public void showExamEnded(String reason) {
        SwingUtilities.invokeLater(() -> {
            String text;
            switch (reason) {
                case "TIME_UP":
                    text = "Hết giờ. Server đã tự chốt bài của bạn.";
                    break;
                case "TEACHER_ENDED":
                    text = "Giáo viên đã kết thúc ca thi.";
                    break;
                case "SUBMITTED":
                    text = "Bạn đã nộp bài.";
                    break;
                default:
                    text = "Ca thi đã kết thúc (" + reason + ").";
                    break;
            }
            lockExam(text);
            if (questions.isEmpty()) {
                loginMessageLabel.setText(text);
            }
        });
    }

    @Override
    public void showNotice(String text) {
        // Hiện trong cửa sổ, không dùng hộp thoại (hộp thoại làm mất focus cửa sổ thi).
        SwingUtilities.invokeLater(() -> noticeLabel.setText("THÔNG BÁO CỦA GIÁO VIÊN: " + text));
    }

    @Override
    public void showRoomStats(String summary) {
        SwingUtilities.invokeLater(() -> roomLabel.setText(summary));
    }

    @Override
    public void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text + "\n"));
    }

    /** Dùng khi chạy với tham số tự đăng nhập. */
    public void clickLoginForAutoStart() {
        SwingUtilities.invokeLater(loginButton::doClick);
    }
}
