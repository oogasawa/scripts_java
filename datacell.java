///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.oogasawa:DataCell:4.0.0

// JBang shortcut for DataCell.
// Provides DataCell data manipulation utilities.
//
// Project: https://github.com/oogasawa/DataCell
//
// Usage:
//   jbang datacell.java [args...]

class cli {
  public static void main(String[] args) throws Exception {
    com.github.oogasawa.datacell.App.main(args);
  }
}
