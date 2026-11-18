"""HOST_UNIT source regression for responsive download catalog. Not Android UI evidence."""
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
ACTIVITY=ROOT/'model-lab/src/main/java/org/localpilot/android/ModelLabActivity.java'
CATALOG=ROOT/'model-lab/src/main/java/org/localpilot/android/ModelLabPresetCatalog.java'

class PresetPickerUiContract(unittest.TestCase):
 def test_pocket_is_first_and_selected_by_default(self):
  catalog=CATALOG.read_text(encoding='utf-8')
  activity=ACTIVITY.read_text(encoding='utf-8')
  self.assertLess(catalog.index('new Preset("pocket"'),catalog.index('new Preset("qwen35"'))
  self.assertIn('presetPicker.setSelection(0); // Pocket first',activity)
  self.assertIn('Ничего не загружено автоматически',activity)
 def test_rows_are_short_and_vision_requirements_remain_explained(self):
  catalog=CATALOG.read_text(encoding='utf-8')
  activity=ACTIVITY.read_text(encoding='utf-8')
  self.assertRegex(catalog,r'return label\+" · "\+\(totalBytes\(\)/1_000_000\)\+" МБ";')
  self.assertIn('Для зрения автоматически загружается соответствующий mmproj',activity)
  self.assertIn('102815168L',catalog)
 def test_popup_wraps_and_respects_available_width(self):
  activity=ACTIVITY.read_text(encoding='utf-8')
  for fragment in ('presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)',
                   'text.setSingleLine(false)',
                   'text.setHorizontallyScrolling(false)',
                   'text.setMaxLines(3)',
                   'text.setEllipsize(null)',
                   'presetPicker.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT)'):
   with self.subTest(fragment=fragment):self.assertIn(fragment,activity)
 def test_selected_model_not_auto_downloaded(self):
  activity=ACTIVITY.read_text(encoding='utf-8')
  self.assertLess(activity.index('presetPicker.setSelection(0)'),activity.index('button(col,"Скачать выбранную модель по проверенной ссылке"'))

if __name__=='__main__':unittest.main()
