from pathlib import Path
import sys
import hashlib
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from run_g2_direct_gguf_acceptance import exact_target
from run_real_nes_vision_acceptance import confirms_control,parse_game_state,correlate_inference,verified_game_progress,valid_game_button

class VisibleSafTargets(unittest.TestCase):
    def test_background_or_wrong_direction_is_not_game_control(self):
        before=parse_game_state('1,100,180,3,3,1,0,0,0,0')
        after=parse_game_state('1,116,180,3,3,1,0,0,1,1')
        self.assertTrue(confirms_control('RIGHT',before,after))
        self.assertTrue(confirms_control('A',before,after))
        self.assertFalse(confirms_control('A',before,before))
        self.assertFalse(confirms_control('LEFT',before,after))
        with self.assertRaises(ValueError):parse_game_state('1,100,180,3,3,1,999999999,0,0,0')
    def test_chord_evidence_is_bounded_and_canonical(self):
        for action in ('RIGHT+A','UP+LEFT+A+B','A+B'):
            self.assertTrue(valid_game_button(action))
        for action in ('UP+DOWN','LEFT+RIGHT+A','A+RIGHT','START+A','A+A','UP+LEFT+A+B+A'):
            self.assertFalse(valid_game_button(action))
        before=parse_game_state('1,100,180,3,3,1,0,0,0,0')
        after=parse_game_state('1,116,180,3,3,1,0,0,1,1')
        self.assertTrue(confirms_control('RIGHT+A',before,after))
        self.assertFalse(confirms_control('RIGHT+A',before,before))

    def test_progress_requires_actual_playing_phase_delta(self):
        playing=parse_game_state('1,100,180,3,3,1,10,0,0,0')
        more=dict(playing,score=20)
        self.assertTrue(verified_game_progress(dict(gameBefore=playing,gameAfter=more)))
        self.assertFalse(verified_game_progress(dict(gameBefore=dict(playing,state=0),gameAfter=more)))
        self.assertFalse(verified_game_progress(dict(gameBefore=playing,gameAfter=dict(more,state=2))))
        self.assertFalse(verified_game_progress(dict(gameBefore=playing,gameAfter=playing)))
        self.assertTrue(verified_game_progress(dict(gameBefore=playing,gameAfter=dict(playing,state=3))))

    def test_start_is_proven_by_real_game_phase_not_pixels(self):
        title=parse_game_state('0,100,180,3,3,1,0,0,0,0')
        playing=parse_game_state('1,100,180,3,3,1,0,0,0,0')
        lost=parse_game_state('2,100,180,0,3,1,0,0,0,0')
        self.assertTrue(confirms_control('START',title,playing))
        self.assertTrue(confirms_control('START',lost,title))
        self.assertFalse(confirms_control('START',playing,playing))
        self.assertFalse(confirms_control('A',title,playing))

    def test_game_geometry_requires_complete_active_coordinates(self):
        state=parse_game_state('1,100,180,3,3,1,0,0,1,1,90,40,104,160')
        self.assertEqual(state['nearestEnemy'],dict(x=90,y=40))
        self.assertEqual(state['nearestHostileBullet'],dict(x=104,y=160))
        absent=parse_game_state('1,100,180,3,3,1,0,0,1,1,-1,-1,-1,-1')
        self.assertIsNone(absent['nearestEnemy'])
        for geometry in ('-1,40,104,160','90,40,104','90,40,104,999'):
            with self.assertRaises(ValueError):parse_game_state('1,100,180,3,3,1,0,0,1,1,'+geometry)

    def test_refuses_wrong_package_before_typing_checksum(self):
        xml='<hierarchy><node class="android.widget.EditText" package="other.app" bounds="[0,0][80,40]"/></hierarchy>'
        with self.assertRaises(RuntimeError):exact_target(xml,'',package='org.localpilot.android.dev.unified',editable=True)

    def test_refuses_ambiguous_inputs_and_disabled_or_empty_file(self):
        node='<node class="android.widget.EditText" package="own" bounds="[0,0][80,40]"/>'
        with self.assertRaises(RuntimeError):exact_target('<hierarchy>'+node+node+'</hierarchy>','',package='own',editable=True)
        for attrs in ('enabled="false" bounds="[0,0][80,40]"','bounds="[0,0][0,0]"'):
            with self.subTest(attrs=attrs),self.assertRaises(RuntimeError):
                exact_target('<hierarchy><node text="fixture.gguf" '+attrs+'/></hierarchy>','fixture.gguf')

    def test_exact_filename_never_matches_similar_document(self):
        xml='<hierarchy><node text="fixture.gguf.backup" bounds="[0,0][80,40]"/></hierarchy>'
        with self.assertRaises(RuntimeError):exact_target(xml,'fixture.gguf')

    def test_latency_comes_only_from_one_matching_native_record(self):
        sha='a'*64
        def record(elapsed=123):
            body=f'NES_MODEL_INFERENCE_V1|import:test|{sha}|20|44|{elapsed}|RIGHT|0'
            return (body+'|'+hashlib.sha256(body.encode()).hexdigest()+'\n').encode()
        rows=[dict(beforeFrame=20,beforeHash='44',button='RIGHT')]
        self.assertEqual(correlate_inference(record(),rows,sha),1)
        self.assertEqual(rows[0]['inferenceElapsedMs'],123)
        with self.assertRaises(ValueError):correlate_inference(record()*2,rows,sha)
        with self.assertRaises(ValueError):correlate_inference(record(-1),rows,sha)
        with self.assertRaises(ValueError):correlate_inference(record(),rows,'b'*64)

    def test_delete_confirmation_matches_android_button_caps_only(self):
        xml='<hierarchy><node text="Удалить выбранный GGUF?" class="android.widget.TextView" package="own" bounds="[0,0][80,40]"/><node text="УДАЛИТЬ" class="android.widget.Button" package="own" bounds="[10,40][110,80]"/></hierarchy>'
        self.assertEqual(exact_target(xml,'Удалить',package='own',button=True),(60,60))
        with self.assertRaises(RuntimeError):exact_target(xml,'Удалить',package='other',button=True)
        with self.assertRaises(RuntimeError):exact_target(xml,'Удалить выбранный',package='own',button=True)

    def test_unique_visible_input(self):
        xml='<hierarchy><node class="android.widget.EditText" package="own" bounds="[10,20][110,60]"/></hierarchy>'
        self.assertEqual(exact_target(xml,'',package='own',editable=True),(60,40))
