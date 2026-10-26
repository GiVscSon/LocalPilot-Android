package org.localpilot.android;

import java.util.Arrays;

/** Immutable observed rectangular field. -1 is unknown; 0 is empty or a recognized protected cell. */
public final class GridPuzzle {
 public final int rows,cols;
 private final int[] cells;

 public GridPuzzle(int rows,int cols,int[] cells){
  if(rows<3||rows>10||cols<3||cols>10||cells==null||cells.length!=rows*cols)throw new IllegalArgumentException("grid must be 3..10 rows and columns");
  this.rows=rows;this.cols=cols;this.cells=cells.clone();
  for(int value:this.cells)if(value< -1)throw new IllegalArgumentException("unsupported cell");
 }
 public int at(int row,int col){if(row<0||row>=rows||col<0||col>=cols)throw new IndexOutOfBoundsException("cell outside grid");return cells[row*cols+col];}
 public int[] cells(){return cells.clone();}
 public boolean known(){for(int value:cells)if(value<0)return false;return true;}
 public boolean same(GridPuzzle other){return other!=null&&rows==other.rows&&cols==other.cols&&Arrays.equals(cells,other.cells);}
 @Override public boolean equals(Object other){return other instanceof GridPuzzle&&same((GridPuzzle)other);}
 @Override public int hashCode(){return 31*(31*rows+cols)+Arrays.hashCode(cells);}
}
