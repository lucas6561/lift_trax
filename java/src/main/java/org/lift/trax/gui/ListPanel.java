package org.lift.trax.gui;

import org.lift.trax.Database;
import org.lift.trax.ExecutionSet;
import org.lift.trax.Lift;
import org.lift.trax.LiftExecution;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ListPanel extends JPanel {
    private final Database db;
    private final DefaultListModel<Lift> liftModel = new DefaultListModel<>();
    private final DefaultListModel<LiftExecution> execModel = new DefaultListModel<>();
    private final JList<Lift> liftList = new JList<>(liftModel);
    private final JList<LiftExecution> execList = new JList<>(execModel);

    public ListPanel(Database db) {
        this.db = db;
        setLayout(new BorderLayout());
        loadLifts();
        liftList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liftList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showExecutions(liftList.getSelectedValue());
            }
        });
        installLiftMenu();
        installExecMenu();
        execList.setCellRenderer(new ExecutionRenderer());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(liftList), new JScrollPane(execList));
        add(split, BorderLayout.CENTER);
    }

    private void loadLifts() {
        liftModel.clear();
        try {
            List<Lift> lifts = db.listLifts(null);
            for (Lift lift : lifts) {
                liftModel.addElement(lift);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showExecutions(Lift lift) {
        execModel.clear();
        if (lift == null) return;
        for (LiftExecution exec : lift.executions) {
            execModel.addElement(exec);
        }
    }

    /**
     * Reload lifts and executions from the database while preserving the
     * currently selected lift.
     */
    public void refresh() {
        Lift selected = liftList.getSelectedValue();
        loadLifts();
        if (selected != null) {
            for (int i = 0; i < liftModel.size(); i++) {
                Lift lift = liftModel.get(i);
                if (lift.name.equals(selected.name)) {
                    liftList.setSelectedIndex(i);
                    showExecutions(lift);
                    break;
                }
            }
        }
    }

    private void installLiftMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit Lift");
        JMenuItem delete = new JMenuItem("Delete Lift");
        menu.add(edit);
        menu.add(delete);
        edit.addActionListener(e -> {
            Lift lift = liftList.getSelectedValue();
            if (lift == null) return;
            String newName = JOptionPane.showInputDialog(this, "New name", lift.name);
            if (newName != null && !newName.isBlank()) {
                try {
                    db.updateLift(lift.name, newName, lift.region, lift.main, lift.muscles, lift.notes);
                    loadLifts();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        delete.addActionListener(e -> {
            Lift lift = liftList.getSelectedValue();
            if (lift == null) return;
            int res = JOptionPane.showConfirmDialog(this, "Delete lift " + lift.name + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                try {
                    db.deleteLift(lift.name);
                    loadLifts();
                    execModel.clear();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        liftList.addMouseListener(new MouseAdapter() {
            private void handle(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = liftList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        liftList.setSelectedIndex(idx);
                        menu.show(liftList, e.getX(), e.getY());
                    }
                }
            }

            @Override public void mousePressed(MouseEvent e) { handle(e); }
            @Override public void mouseReleased(MouseEvent e) { handle(e); }
        });
    }

    private void installExecMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit Execution");
        JMenuItem delete = new JMenuItem("Delete Execution");
        menu.add(edit);
        menu.add(delete);
        edit.addActionListener(e -> {
            LiftExecution exec = execList.getSelectedValue();
            if (exec == null) return;
            String notes = JOptionPane.showInputDialog(this, "Notes", exec.notes);
            if (notes != null) {
                exec.notes = notes;
                try {
                    db.updateLiftExecution(exec.id, exec);
                    execList.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        delete.addActionListener(e -> {
            LiftExecution exec = execList.getSelectedValue();
            if (exec == null) return;
            int res = JOptionPane.showConfirmDialog(this, "Delete execution?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                try {
                    db.deleteLiftExecution(exec.id);
                    loadLifts();
                    showExecutions(liftList.getSelectedValue());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        execList.addMouseListener(new MouseAdapter() {
            private void handle(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = execList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        execList.setSelectedIndex(idx);
                        menu.show(execList, e.getX(), e.getY());
                    }
                }
            }

            @Override public void mousePressed(MouseEvent e) { handle(e); }
            @Override public void mouseReleased(MouseEvent e) { handle(e); }
        });
    }

    private static class ExecutionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof LiftExecution exec) {
                StringBuilder sb = new StringBuilder("<html");
                sb.append('>').append(exec.date);
                if (exec.warmup) {
                    sb.append(" (warm-up)");
                }
                int setNum = 1;
                for (ExecutionSet set : exec.sets) {
                    sb.append("<br>").append(setNum++).append(": ")
                            .append(set.displayWeight());
                    if (set.reps != null) {
                        sb.append(" x ").append(set.reps);
                    } else if (set.timeSecs != null) {
                        sb.append(" for ").append(set.timeSecs).append(" sec");
                    } else if (set.distanceFeet != null) {
                        sb.append(" for ").append(set.distanceFeet).append(" ft");
                    }
                    if (set.rpe != null) {
                        sb.append(" @ RPE ").append(set.rpe);
                    }
                }
                if (exec.notes != null && !exec.notes.isBlank()) {
                    sb.append("<br>").append(exec.notes);
                }
                sb.append("</html>");
                label.setText(sb.toString());
                label.setVerticalAlignment(SwingConstants.TOP);
            }
            return label;
        }
    }
}
